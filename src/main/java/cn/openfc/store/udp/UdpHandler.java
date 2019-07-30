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
package cn.openfc.store.udp;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.openfc.store.service.BizService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket>
{

   private static Logger logger = LoggerFactory.getLogger(UdpHandler.class);
   BizService bizService;

   public UdpHandler(BizService bizService)
   {
      this.bizService = bizService;
   }

   @Override
   protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception
   {

      ByteBuf buf = packet.content();
      String line = buf.toString(CharsetUtil.UTF_8);
      logger.debug("\n" + line);
      String predict =  bizService.act(line);
      logger.info("=============="+predict);
      if(StringUtils.isBlank(predict))
      {
         predict="-1";
      }
//      ctx.writeAndFlush(predict);
      ctx.channel().writeAndFlush(predict);
   }

}
