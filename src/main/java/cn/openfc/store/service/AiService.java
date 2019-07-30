/*
 * Copyright 2019 The OpenFC Project
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import cn.openfc.store.utils.JsonUtil;

public class AiService
{
   private static Logger logger = LoggerFactory.getLogger(AiService.class);
   
   private Map<String, String> paramMap = new HashMap<String, String>();
   private Map<String, String> commonMap;
   private CloseableHttpClient httpClient;
   public void init()
   {
      param(true);
      Runnable runnable = new Runnable()
      {
         public void run()
         {
            logger.info("检查ai模板任务--开始");
            param(false);
            logger.info("检查ai模板任务--结束");
         }
      };
      ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
      service.scheduleAtFixedRate(runnable, RandomUtils.nextInt(10), 10, TimeUnit.SECONDS);
      
      PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
      connectionManager.setMaxTotal(100);
      connectionManager.setDefaultMaxPerRoute(30);
      httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
   }

   public String predict(Map<String, Object> map) 
   {
      Integer index=0;
      String key = map.get("type")+"-"+map.get("version");
      if(paramMap.containsKey(key))
      {
         String paramOrder = paramMap.get(key);
         String[] params = paramOrder.split(",");
         List<Float> list = new ArrayList<Float>();
         
         for(String p:params)
         {
            list.add(Float.valueOf(map.get(p).toString()));
         }
         Object[] arr = list.toArray();
         Object[][] xx= new Object[1][4];
         xx[0]= arr;
         Map<String,Object[][]> dataMap = new HashMap<String,Object[][]>();
         dataMap.put("instances",xx);
         String body = JsonUtil.json(dataMap);
         HttpPost httpPost = new HttpPost(commonMap.get("ai.url"));
         httpPost.setEntity(new StringEntity(body, "UTF-8"));
         CloseableHttpResponse response;
         
         try
         {
            response = httpClient.execute(httpPost);
            String result = EntityUtils.toString(response.getEntity());
            JsonNode jsonNode = JsonUtil.read(result,JsonNode.class);
            jsonNode = jsonNode.get("predictions");
            
           
            if(jsonNode.isArray())
            {
               for(JsonNode node:jsonNode)
               {
                  int i=0;
                  double tmp=0;
                  for(JsonNode n:node)
                  {
                     double data = n.asDouble();
                     if(data>tmp)
                     {
                        tmp=data;
                        index=i;
                     }
                     i++;
                  }
               }
               
            }
            
         } catch (Exception e)
         {
            logger.error(e.getMessage(), e);
         } 
         
      }
      return index.toString();
   }

   private void param(boolean init)
   {

      try
      {

         List<String> paramList = new ArrayList<String>();

         String[] suffix = { "txt" };
         String[] initSuffix = { "txt", "read" };
         if (init)
            suffix = initSuffix;
         Collection<File> fileList = FileUtils.listFiles(FileUtils.getFile(commonMap.get("ai.param.path")), suffix,
               false);
         for (File file : fileList)
         {
            String content = FileUtils.readFileToString(file, "UTF-8");
            List<String> list = FileUtils.readLines(file, "UTF-8");
            logger.info("=========param文件:{}==========\n{}", file.getPath(), content);
            for (String line : list)
            {
               if (StringUtils.isNotBlank(line))
               {
                  paramList.add(line.trim());
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

         for (String tagLine : paramList)
         {
            String[] tag = tagLine.split("=");
            String name = tag[0];
            String content = tag[1];
            if (paramMap.containsKey(name))
            {
               logger.warn("请检查param文件,param定义重复:{}", tagLine);
            }
            paramMap.put(name, content);
         }

      } catch (IOException e)
      {
         logger.error(e.getMessage(), e);
      }

   }

   public void setCommonMap(Map<String, String> commonMap)
   {
      this.commonMap = commonMap;
   }

}
