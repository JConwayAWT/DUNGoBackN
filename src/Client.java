import java.awt.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

class Client implements Runnable{
	public static int sequenceNumber = 0;
	public static int windowSize = 7;
	public static int oldestSentButUnackedPacketNumber = 0;
	
	public static Boolean eclipseMode = true;
	public static String emulatorName = "localhost";
	public static int sendToEmulator = 6000;
	public static int receiveFromEmulator = 6001;
	public static String fileName = "inputFile.txt";
	public static PrintWriter seqnumlog;
	public static PrintWriter acklog;
	public static DatagramSocket dgsIn;
	
	public static void main(String args[]) throws Exception{
		setCommandLineVariables(args);
		
		DatagramSocket dgsOut = new DatagramSocket();
		InetAddress iaOut = InetAddress.getByName(emulatorName);
		dgsOut.connect(iaOut, sendToEmulator);
		
		dgsIn = new DatagramSocket(receiveFromEmulator);
		
		seqnumlog = new PrintWriter("seqnum.log","UTF-8");
		acklog = new PrintWriter("ack.log","UTF-8");
		
		String content = readFileContentsFrom(fileName);
		ArrayList<packet> packetsList = new ArrayList<packet>();
		packetsList = createAllPacketsFor(content, packetsList);
		int totalPackets = packetsList.size();
		
		//Fire off the first bunch.
		for(int i = 0; i < windowSize; i++){
			sendDatagramPacket(packetsList.get(i), dgsOut, iaOut);
			sequenceNumber++;
		} //now sequence number = 7
		
		
		dgsIn.setSoTimeout(800);
		while(oldestSentButUnackedPacketNumber < totalPackets)
		{
			try{
				packet pIn = receiveDatagramAndConvert();
				int ackNumber = pIn.getSeqNum();
				if (ackNumber > oldestSentButUnackedPacketNumber){
					//We received the correct packet.
					oldestSentButUnackedPacketNumber = ackNumber + 1;
					sequenceNumber = oldestSentButUnackedPacketNumber + windowSize;
					if (sequenceNumber < totalPackets){sendDatagramPacket(packetsList.get(sequenceNumber), dgsOut, iaOut);}
				}
				else{
					//We got the wrong packet =( Start again from the last sent but unacked packet.
					sendDatagramPacket(packetsList.get(oldestSentButUnackedPacketNumber), dgsOut, iaOut);
					sequenceNumber = oldestSentButUnackedPacketNumber;
				}
			}
			catch(SocketTimeoutException e){
				//hm, we're not supposed to have any timeouts...
				sequenceNumber = 100;
			}
		}
		
		sendEndOfTransmission(dgsOut, iaOut, totalPackets);
		acklog.close();
		seqnumlog.close();
		
	}
	
	
	public static void sendEndOfTransmission(DatagramSocket dgsOut, InetAddress iaOut, int totalPackets
		) throws UnknownHostException, IOException, ClassNotFoundException{
		packet p = new packet(3, totalPackets, 0, "");
		sendDatagramPacket(p, dgsOut, iaOut);
		packet pIn = receiveDatagramAndConvert();
	}
	
	public static void sendDatagramPacket(packet p, DatagramSocket dgs, InetAddress ia) throws IOException, ClassNotFoundException{
		byte[] sendBuf = makeByteArrayFromPacket(p);
		seqnumlog.println(p.getSeqNum());
		DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, ia, 6000);
		dgs.send(packet);
		printSentPacketToConsole(p);
	}
	
	public static packet receiveDatagramAndConvert() throws IOException, ClassNotFoundException{
		byte[] recBuf = new byte[1024];
		DatagramPacket recpacket = new DatagramPacket(recBuf, recBuf.length);
		
		dgsIn.receive(recpacket); //<!!--THIS IS A BLOCKING CALL OMG--!!>
		
		ByteArrayInputStream inSt = new ByteArrayInputStream(recBuf);
		ObjectInputStream oinSt = new ObjectInputStream(inSt);        
		packet p = (packet) oinSt.readObject();
		acklog.println(p.getSeqNum());
		printReceivedPacketToConsole(p);
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
	
	public static packet makePacketFromByteArray(byte[] byteArray) throws IOException, ClassNotFoundException{
		packet p;
		ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);
		ObjectInput in = new ObjectInputStream(bais);
		p = (packet) in.readObject();
		printReceivedPacketToConsole(p);
		return p;
	}
	
 	public static String readFileContentsFrom(String fileName) throws Exception {
 		//Read the file and get its contents
 		Scanner inputScanner = new Scanner(new File(fileName));
 		String content = inputScanner.useDelimiter("\\Z").next();
 		inputScanner.close();
 		
 		return content;
 	}


	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
	
	public static ArrayList<packet> createAllPacketsFor(String content, ArrayList<packet> packetsList){
		int localSequenceNumber = 0;
		while (content.length() > 0){
			if (content.length() > 16){
				packet newPacket = new packet(1, localSequenceNumber, 16, content.substring(0, 16));
				content = content.substring(16, content.length());
				packetsList.add(newPacket);
			}
			else{
				packet newPacket = new packet(1, localSequenceNumber, content.length(), content);
				content = "";
				packetsList.add(newPacket);
			}
			localSequenceNumber++;
		}
		
		return packetsList;
	}
	
	public static void setCommandLineVariables(String args[]){
		if (!eclipseMode){
			emulatorName = args[0];
			sendToEmulator = Integer.parseInt(args[1]);
			receiveFromEmulator = Integer.parseInt(args[2]);
			fileName = args[3];
		}
	}
	
	public static void printSentPacketToConsole(packet p){
		System.out.println("Client just sent a packet: ");
		p.printContents();
	}
	
	public static void printReceivedPacketToConsole(packet p){
		System.out.println("Client just received a packet: ");
		p.printContents();
	}
}