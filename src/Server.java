import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;

class Server implements Runnable{
	public static Boolean eclipseMode = true;
	public static Integer expectedSequenceNumber = 0;
	public static String emulatorName;
	public static Integer receiveFromEmulator;
	public static Integer sendToEmulator;
	public static String fileName;
	public static PrintWriter arrivallog;
	public static PrintWriter fileWriter;
	
	public static void main(String args[]) throws Exception{
		Boolean continueWorking = true;
		Boolean dropPacket15 = true;
		fileWriter = new PrintWriter("received.txt","UTF-8");
		arrivallog = new PrintWriter("arrival.log","UTF-8");
		setCommandLineVariables(args);
		
		//Connect to the emulator; keep these variables around
		DatagramSocket dgsOut = new DatagramSocket();
		InetAddress iaOut = InetAddress.getByName(emulatorName);
		dgsOut.connect(iaOut, sendToEmulator);
		
		//Let things come in from the emulator; keep these variables around
		DatagramSocket dgsIn;
		dgsIn = new DatagramSocket(receiveFromEmulator);
		
		while (continueWorking){
			packet p = receiveDatagramAndConvert(dgsIn);
			
			if (p.getType() == 1){
				//it's a data packet
				if (p.getSeqNum() == expectedSequenceNumber){ //<--THIS RIGHT HERE IS THE TRUE CODE
					fileWriter.print(p.getData());
					sendAckPacket(p, dgsOut, iaOut);
					expectedSequenceNumber++;
				}
				else{
					sendExpectationPacket(dgsOut, iaOut);
				}
			}
			else{
				//it's an EOT packet
				continueWorking = false;
				sendEndOfTransmissionPacket(p, dgsOut, iaOut);
			}
		}		
		
		fileWriter.close();
		arrivallog.close();
		dgsIn.close();
		dgsOut.close();
		System.out.println("Server down.");
		
	}
	
	public static packet receiveDatagramAndConvert(DatagramSocket dgsIn) throws IOException, ClassNotFoundException{
		byte[] recBuf = new byte[1024];
		DatagramPacket recpacket = new DatagramPacket(recBuf, recBuf.length);
		
		dgsIn.receive(recpacket); //<!!--THIS IS A BLOCKING CALL OMG--!!>
		
		ByteArrayInputStream inSt = new ByteArrayInputStream(recBuf);
		ObjectInputStream oinSt = new ObjectInputStream(inSt);        
		packet p = (packet) oinSt.readObject();
		printReceivedPacketToConsole(p);
		arrivallog.println(p.getSeqNum());
		return p;
	}
	public static void sendAckPacket(packet p, DatagramSocket dgs, InetAddress ia) throws IOException{
		packet pOut = new packet(0, p.getSeqNum(), 0, "");
		byte[] sendBuf = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(sendBuf, sendBuf.length, ia, sendToEmulator);
		dgs.send(dgPacket);
		printSentPacketToConsole(p);
	}
	
	public static void sendExpectationPacket(DatagramSocket dgsOut, InetAddress ia) throws IOException{
		packet pOut = new packet(0, expectedSequenceNumber,0, "");
		byte[] packetAsBytes = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(packetAsBytes, packetAsBytes.length, ia, sendToEmulator);
		dgsOut.send(dgPacket);
		printSentPacketToConsole(pOut);
	}
	
	public static void sendEndOfTransmissionPacket(packet pIn, DatagramSocket dgsOut, InetAddress ia) throws IOException{
		packet pOut = new packet(2, pIn.getSeqNum(),0,"");
		byte [] packetAsBytes = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(packetAsBytes, packetAsBytes.length, ia, sendToEmulator);
		dgsOut.send(dgPacket);
		printSentPacketToConsole(pOut);
	}
	
	public static packet makePacketFromByteArray(DatagramPacket dgp, byte[] buff) throws IOException, ClassNotFoundException{
		packet p;
		ByteArrayInputStream bais = new ByteArrayInputStream(buff);
		ObjectInputStream oinSt = new ObjectInputStream(bais);
		p = (packet) oinSt.readObject();
		return p;
	}
	
	public static byte[] makeByteArrayFromPacket(packet p) throws IOException{
		ByteArrayOutputStream oSt = new ByteArrayOutputStream();
		ObjectOutputStream ooSt = new ObjectOutputStream(oSt);
		ooSt.writeObject(p);
		ooSt.close();
		byte[] sendBuf = oSt.toByteArray();
		return sendBuf;
	}
	
	public static void setCommandLineVariables(String args[]){
		if (eclipseMode){
			emulatorName = "localhost";
			receiveFromEmulator = 6002;
			sendToEmulator = 6000;
			fileName = "inputFile.txt";
		}
		else{
			emulatorName = args[0];
			receiveFromEmulator = Integer.parseInt(args[1]);
			sendToEmulator = Integer.parseInt(args[2]);
			fileName = args[3];
		}
	}
	
	public static void printSentPacketToConsole(packet p){
		System.out.println("Server just sent a packet: ");
		p.printContents();
	}
	
	public static void printReceivedPacketToConsole(packet p){
		System.out.println("Server just received a packet: ");
		p.printContents();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
}