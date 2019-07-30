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
package cn.openfc.store.common;

public class StoreException extends Exception
{

   private static final long serialVersionUID = 1L;

   private Integer code;
   private String message;

   public StoreException(Integer code)
   {
      this.code = code;
   }

   public StoreException(Integer code, String message)
   {
      super(message);
      this.code = code;
      this.message = message;
   }

   public Integer getCode()
   {
      return code;
   }

   public String getMessage()
   {
      return message;
   }

}
