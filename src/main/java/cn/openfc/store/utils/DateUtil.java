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
package cn.openfc.store.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil
{

   /**
    * 得到日期时间字符串 精确到秒
    */
   public static String format(Date date)
   {
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      return df.format(date);
   }

   public static String secondTime()
   {
      SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
      return df.format(new Date());
   }

   public static String formatDay()
   {
      SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
      return df.format(new Date());
   }

}
