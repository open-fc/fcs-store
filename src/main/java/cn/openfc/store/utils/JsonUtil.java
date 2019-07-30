package cn.openfc.store.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtil
{
   private static Logger log = LoggerFactory.getLogger(JsonUtil.class);
   private static ObjectMapper mapper = new ObjectMapper();

   public static String json(Object object)
   {
      try
      {
         return mapper.writeValueAsString(object);
      } catch (Exception e)
      {
         e.printStackTrace();
         return null;
      }
   }

   public static JsonNode jsonNode(Object object)
   {
      try
      {
         return mapper.valueToTree(object);
      } catch (Exception e)
      {
         e.printStackTrace();
         return null;
      }
   }

   public static <T> T read(String json, Class<T> classz)
   {
      try
      {
         return mapper.readValue(json, classz);
      } catch (Exception e)
      {
         log.error(e.getMessage(), e);
         return null;
      }
   }

   public static ObjectNode objectNode()
   {
      return mapper.createObjectNode();
   }
}
