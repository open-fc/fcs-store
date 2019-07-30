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
package cn.openfc.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import cn.openfc.store.service.BizService;
import cn.openfc.store.udp.UdpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class Main
{
   private static Logger logger = LoggerFactory.getLogger(Main.class);

   public static void main(String[] args)
   {
      ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "applicationContext.xml" });
      context.start();

      BizService bizService = context.getBean("bizService", BizService.class);
      logger.info("server starting...");

      EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(eventLoopGroup).channel(NioDatagramChannel.class).option(ChannelOption.IP_MULTICAST_TTL,200)
            .option(ChannelOption.SO_BROADCAST, true).option(ChannelOption.SO_RCVBUF,1024*1024*10);//10M缓存

      try
      {
            bootstrap.handler(new UdpHandler(bizService));
            Channel channel = bootstrap.bind(bizService.bindPort()).sync().channel();
            channel.closeFuture().sync();
     
      } catch (InterruptedException e)
      {
         logger.error(e.getMessage(),e);
      } finally
      {
         eventLoopGroup.shutdownGracefully();
      }

   }

}
