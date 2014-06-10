import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class javatest{
	public static void main(String args[]) throws InterruptedException, IOException{
		//Connect to the emulator; keep these variables around
		DatagramSocket dgsOut = new DatagramSocket(6002);
		
		//Let things come in from the emulator; keep these variables around
		//DatagramSocket dgsIn;
		//dgsIn = new DatagramSocket(6002);
		
		packet p = new packet(0, 0, 2, "hi");
		
		sendAckPacket(p, dgsOut);
		System.out.println("sent");
		
		
	}
	
	public static void sendAckPacket(packet p, DatagramSocket dgs) throws IOException{
		packet pOut = new packet(0, (p.getSeqNum()+1)%8, 0, "");
		byte[] sendBuf = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(sendBuf, sendBuf.length);
		dgs.send(dgPacket);
		System.out.println("SERVER: Just sent a packet with sequence number: " + pOut.getSeqNum());
	}
	
	public static byte[] makeByteArrayFromPacket(packet p) throws IOException{
		ByteArrayOutputStream oSt = new ByteArrayOutputStream();
		ObjectOutputStream ooSt = new ObjectOutputStream(oSt);
		ooSt.writeObject(p);
		ooSt.close();
		byte[] sendBuf = oSt.toByteArray();
		return sendBuf;
	}
}