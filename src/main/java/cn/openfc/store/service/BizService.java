/*
 * Copyright 2018 The OpenFC Project
 *
 * The OpenFC Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package cn.openfc.store.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import cn.openfc.store.utils.DateUtil;



public class BizService
{

   private static Logger logger = LoggerFactory.getLogger(BizService.class);

   private ConcurrentLinkedQueue<String> lineQueue = new ConcurrentLinkedQueue<String>();// 使用队列缓存数据
   private ConcurrentLinkedQueue<Map<String, Object>> mapQueue = new ConcurrentLinkedQueue<Map<String, Object>>();// 使用队列缓存数据

   ExecutorService executor = Executors.newFixedThreadPool(20);

   private AiService aiService;
   private MongoClient mongoClient;
   private MongoDatabase database;
   private Map<String, String> commonMap;

   private CloseableHttpClient httpClient;
   
   
   private String rootPath;// 文件存储基本目录 D:/data/
   private File file;
   private String fileName;
   private String path;// 相对路径 20180724/201807241849.cvs
   private boolean writeFile;
   private boolean writeInfluxDB;
   private boolean writeMongoDB;
   private boolean replaceTime;
   private String timeKey;
   private int limit;//文件最大行数限制
   private int count = 0;//当前文件已经写入行数

   //报文模板
   Map<String, String[]> templateMap = new HashMap<String, String[]>();
   //influx tag定义
   Map<String, String> tagMap = new HashMap<String, String>();

   public void init()
   {

      PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
      connectionManager.setMaxTotal(100);
      connectionManager.setDefaultMaxPerRoute(100);
      httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
      
      rootPath = commonMap.get("file.root.path");
      limit = Integer.valueOf(commonMap.get("file.write.limit"));
      timeKey = commonMap.get("replace.timestamp.key");
      replaceTime = BooleanUtils.toBoolean(commonMap.get("replace.timestamp.enable"));
      writeFile = BooleanUtils.toBoolean(commonMap.get("file.enable"));
      writeInfluxDB = BooleanUtils.toBoolean(commonMap.get("influx.enable"));
      writeMongoDB = BooleanUtils.toBoolean(commonMap.get("mongo.enable"));
      if (writeMongoDB)
      {
         mongoClient = MongoClients.create(commonMap.get("mongo.url"));
         database = mongoClient.getDatabase(commonMap.get("mongo.dbname"));
      }

      template(true);
      tag(true);

      Runnable runnable = new Runnable()
      {
         public void run()
         {
            logger.info("检查模板任务--开始");
            template(false);
            tag(false);
            logger.info("检查模板任务--结束");

            logger.info("入库任务--开始");
            write();
            logger.info("入库任务--结束");
         }
      };
      ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
      service.scheduleAtFixedRate(runnable, RandomUtils.nextInt(10), 10, TimeUnit.SECONDS);

   }

   /**
    * 处理中心
    */
   public String act(String line)
   {
      List<String> lines = new ArrayList<String>();
      lines.add(line);
      List<Map<String, Object>> dataList = parse(lines);

      if (dataList.size() > 0)
      {
         Map<String, Object> map = dataList.get(0);
         if (writeFile)
         {
            lineQueue.add(lines.get(0));
         }

         if (writeMongoDB)
         {
            mapQueue.add(map);//mongodb入库使用的队列
         }

         if (writeInfluxDB)
         {
            executor.submit(new influx(dataList));
         }

         //AI 处理
         //@TODO 需要改
         String predict = aiService.predict(map);
         logger.info("-----------"+predict);
         return predict;
      }
      //@TODO 需要改
      return null;
   }

   /**
    * 解析报文
    */
   private List<Map<String, Object>> parse(List<String> lines)
   {
      List<Map<String, Object>> dataList = new ArrayList<Map<String, Object>>();
      for (String line : lines)
      {
         Map<String, Object> lineMap = new HashMap<String, Object>();

         String[] fields = StringUtils.splitPreserveAllTokens(line, ",");
         String name = fields[0] + "-" + fields[1];

         String[] template = templateMap.get(name);
         if (template == null)
         {
            logger.warn("无对应模板:{}", line);
            continue;
         }
         if (template.length != fields.length)
         {
            logger.warn("数据格式不对:{}", line);
            continue;
         }
         for (int i = 0; i < template.length; i++)
         {
            String[] ft = template[i].split("-");
            String type = ft[1].toLowerCase();
            String key = ft[0];
            String data = StringUtils.trim(fields[i]);
            Object value = null;
            if (StringUtils.isBlank(data))
            {
               continue;
            }
            if ("string".equals(type))
            {
               value = data;
            }

            if ("int,integer".contains(type))
            {
               value = Integer.valueOf(data);
            }

            if ("long".equals(type))
            {
               value = Long.valueOf(data);
            }

            if ("float,double".contains(type))
            {
               value = NumberUtils.createBigDecimal(data);
            }

            if (value != null)
            {
               lineMap.put(key, value);
            }

         }

         if (replaceTime)
         {
            long time = System.currentTimeMillis() / 1000;
            line = line.replace(String.valueOf(lineMap.get(timeKey)), time + "");
            lineMap.put(timeKey, time);
         }
         dataList.add(lineMap);
      }

      return dataList;
   }

   /**
    * 定时写入工作
    */
   private void write()
   {

      //写入到文件
      String tmp;
      int count = 0;//局部变量,只在本函数内有效
      List<String> lines = new ArrayList<String>();
      while ((tmp = lineQueue.poll()) != null)
      {
         lines.add(tmp.trim());
         count++;
         if (count > 1000)
         {
            saveLines(lines);
            lines.clear();
            count = 0;
         }
      }
      saveLines(lines);

      //写入mongodb
      count = 0;
      Map<String, Object> map;
      List<Map<String, Object>> dataList = new ArrayList<Map<String, Object>>();//parse(lines);
      while ((map = mapQueue.poll()) != null)
      {
         dataList.add(map);
         count++;
         if (count > 1000)
         {
            saveMongo(dataList);
            dataList.clear();
            count = 0;
         }

      }
      saveMongo(dataList);

   }

   /**
    * 保存到文件
    */
   private void saveLines(List<String> lines)
   {
      try
      {
         if (StringUtils.isBlank(fileName) || count > limit)//初始化
         {
            createFile();
            count = 0;
         }
         logger.info("本次写入文件条数:{}", lines.size());
         FileUtils.writeLines(file, lines, true);
         count += lines.size();

      } catch (Exception e)
      {
         logger.error(e.getMessage(), e);
      }

   }

   /**
    * 提交多行
    */
   private void saveInflux(List<Map<String, Object>> dataList)
   {

      StringBuilder sb = new StringBuilder();
      for (Map<String, Object> map : dataList)
      {

         String head = map.get("type").toString() + "-" + map.get("version").toString();
         if (tagMap.containsKey(head))
         {
            String tags = tagMap.get(head);

            StringBuilder tag = new StringBuilder();
            StringBuilder field = new StringBuilder();
            for (Entry<String, Object> entry : map.entrySet())
            {
               String key = entry.getKey();
               if ("time".equals(key))
               {
                  continue;
               }

               Object ov = entry.getValue();
               String value = ov.toString();
               if (tags.contains(key))
               {
                  tag.append("," + key + "=" + value);
               } else
               {
                  if (ov instanceof String)
                  {
                     value = "\"" + value + "\"";
                  }
                  field.append("," + key + "=" + value);
               }

            }
            //需要优化 TODO
            if ("CLCT".equals(map.get("type")))
            {
               float v = Float.valueOf(map.get("voltage").toString());
               float c = Float.valueOf(map.get("current").toString());
               field.append(",power=" + v * c);
            }
            //clct,region=us-west,host=server01 value=0.64,value2=33 1434055521
            sb.append("fcms").append(tag).append(field.toString().replaceFirst(",", " ")).append(" ")
                  .append(map.get("time")).append("\n");

         }

      }

      logger.info("提交内容:\n{}", sb.toString());

      HttpPost httpPost = new HttpPost(commonMap.get("influx.url"));
      httpPost.setEntity(new StringEntity(sb.toString(), "UTF-8"));

      try
      {

         CloseableHttpResponse response = httpClient.execute(httpPost);
         logger.info(response.getStatusLine().toString());
         EntityUtils.consume(response.getEntity());

      } catch (Exception e)
      {
         logger.error(e.getMessage(), e);
      }

   }

   /**
    * 存入mongodb数据库
    */
   private void saveMongo(List<Map<String, Object>> dataList)
   {
      MongoCollection<Document> collection = database.getCollection("fcs");
      List<Document> list = new ArrayList<Document>();

      for (Map<String, Object> map : dataList)
      {
         Document doc = new Document();
         doc.putAll(map);
         list.add(doc);
      }

      collection.insertMany(list);
   }

   /**
    * 创建文件
    */
   private void createFile()
   {
      fileName = DateUtil.secondTime() + ".csv";
      path = DateUtil.formatDay() + File.separatorChar + fileName;
      String fullPath = rootPath + File.separatorChar + path;
      logger.info("创建文件:" + fullPath);
      file = FileUtils.getFile(fullPath);
   }

   private void template(boolean init)
   {

      try
      {
         List<String> templateList = new ArrayList<String>();

         String[] suffix = { "txt" };
         String[] initSuffix = { "txt", "read" };
         if (init)
            suffix = initSuffix;
         Collection<File> fileList = FileUtils.listFiles(FileUtils.getFile(commonMap.get("mongo.template.path")),
               suffix, false);
         for (File file : fileList)
         {
            String content = FileUtils.readFileToString(file, "UTF-8");
            List<String> list = FileUtils.readLines(file, "UTF-8");
            logger.info("=========模板文件:{}==========\n{}", file.getPath(), content);
            for (String line : list)
            {
               if (StringUtils.isNotBlank(line))
               {
                  templateList.add(line.trim());
               }

            }

            String fileName = file.getName();
            if (!fileName.contains("read"))
            {
               fileName += ".read";
               File newFile = FileUtils.getFile(file.getParentFile(), fileName);
               file.renameTo(newFile);
            }

         }

         for (String tmp : templateList)
         {
            String[] template = tmp.split("=");
            String name = template[0];
            String[] content = template[1].split(",");
            if (templateMap.containsKey(name))
            {
               logger.warn("请检查模板文件,报文定义重复:{}", tmp);
            }
            templateMap.put(name, content);
         }

      } catch (IOException e)
      {
         logger.error(e.getMessage(), e);
      }

   }

   private void tag(boolean init)
   {
      try
      {
         List<String> tagList = new ArrayList<String>();
         String[] suffix = { "txt" };
         String[] initSuffix = { "txt", "read" };
         if (init)
            suffix = initSuffix;
         Collection<File> fileList = FileUtils.listFiles(FileUtils.getFile(commonMap.get("influx.tag.path")), suffix,
               false);
         for (File file : fileList)
         {
            String content = FileUtils.readFileToString(file, "UTF-8");
            List<String> list = FileUtils.readLines(file, "UTF-8");
            logger.info("=========tag文件:{}==========\n{}", file.getPath(), content);
            for (String line : list)
            {
               if (StringUtils.isNotBlank(line))
               {
                  tagList.add(line.trim());
               }

            }

            String fileName = file.getName();
            if (!fileName.contains("read"))
            {
               fileName += ".read";
               File newFile = FileUtils.getFile(file.getParentFile(), fileName);
               file.renameTo(newFile);
            }

         }

         for (String tagLine : tagList)
         {
            String[] tag = tagLine.split("=");
            String name = tag[0];
            String content = tag[1];
            if (tagMap.containsKey(name))
            {
               logger.warn("请检查tag文件,tag定义重复:{}", tagLine);
            }
            tagMap.put(name, content);
         }

      } catch (IOException e)
      {
         logger.error(e.getMessage(), e);
      }

   }

   public int bindPort()
   {
      return Integer.valueOf(commonMap.get("server.port"));
   }

   public void setCommonMap(Map<String, String> commonMap)
   {
      this.commonMap = commonMap;
   }

   public void close()
   {
      if (writeMongoDB)
      {
         mongoClient.close();
      }

   }

   public void setAiService(AiService aiService)
   {
      this.aiService = aiService;
   }

   private class influx implements Runnable
   {
      List<Map<String, Object>> dataList;
      influx(List<Map<String, Object>> dataList)
      {
         this.dataList = dataList;
      }
      @Override
      public void run()
      {
         saveInflux(dataList);
      }
   }

}
