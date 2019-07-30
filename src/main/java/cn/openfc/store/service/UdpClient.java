package cn.openfc.store.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClient {

	private final static int PORT = 6789;
	private static final String HOSTNAME = "localhost";

	public static void main(String[] args) throws Exception {
		// 传入0表示让操作系统分配一个端口号
		try (DatagramSocket socket = new DatagramSocket(0)) {
			socket.setSoTimeout(10000);
			InetAddress host = InetAddress.getByName(HOSTNAME);
			String context = "CLCT,10,05c1b0ee-b6c2-49e4-a9d4-27d3adffdbee,1561394649,53556,23813,AUTO,B,69.14,-44.66,50.20,-44.66,50.20,0.00,0.00,7297";
			byte[]  cb = context.getBytes("UTF-8");
			// 指定包要发送的目的地
			DatagramPacket request = new DatagramPacket(cb, cb.length, host, PORT);
		   System.out.println("client=ddddddddddddddddddd");
		   
		   socket.send(request);
	     
	       
			// 为接受的数据包创建空间
			DatagramPacket response = new DatagramPacket(new byte[1024], 1024);

			socket.receive(response);
			String result = new String(response.getData(), 0, response.getLength(), "ASCII");
			System.out.println("result="+result);
	       
	       socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
