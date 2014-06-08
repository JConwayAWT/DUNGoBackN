import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

class Client{
	public static int windowSize = 7;
	public static Boolean eclipseMode = true;
	public static String emulatorName = "localhost";
	public static int sendToEmulator = 6000;
	public static int receiveFromEmulator = 6001;
	public static String fileName = "inputFile.txt";
	public static PrintWriter seqnumlog;
	public static PrintWriter acklog;
	public static DatagramSocket dgsIn;
	public static ArrayList<Integer> lastSentAtPosition;
	
	public static void main(String args[]) throws Exception{
		setCommandLineVariables(args);
		lastSentAtPosition = new ArrayList<Integer>(windowSize+1);
		initializeSends();
		
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
			if(i < totalPackets){
				sendDatagramPacket(packetsList.get(i), dgsOut, iaOut);
			}
		} 
		
		dgsIn.setSoTimeout(800);
		int timesToListen = Math.min(7, totalPackets);
		int ackNumber = 0;
		int startIndex = 0;
		
		while (startIndex < totalPackets){
			try{
				for(int i = 0; i < timesToListen; i++){
					ackNumber = receiveDatagramAndConvert().getSeqNum();
				}
				int indexer = ((((ackNumber - 1) % 8) + 8) % 8);
				startIndex = lastSentAtPosition.get(indexer)+1;
				say("Start index now = " + startIndex);
				timesToListen = sendAllPacketsInclusiveFromXtoY(startIndex, startIndex+6, packetsList, dgsOut, iaOut);
			}
			catch(SocketTimeoutException e){
				timesToListen = 0;
			}
		}
		
		sendEndOfTransmission(dgsOut, iaOut, totalPackets);
		acklog.close();
		seqnumlog.close();
		say("CLIENT: Client closed.");
		
	}
	
	public static void say(String s){
		System.out.println(s);
	}
	
	public static void sendEndOfTransmission(DatagramSocket dgsOut, InetAddress iaOut, int totalPackets
			) throws UnknownHostException, IOException, ClassNotFoundException{
			packet p = new packet(3, totalPackets%8, 0, "");
			sendDatagramPacket(p, dgsOut, iaOut);
			packet pIn = receiveDatagramAndConvert();
	}
	
	public static int sendAllPacketsInclusiveFromXtoY(int first, int last, ArrayList<packet> pList, 
	DatagramSocket dgsOut, InetAddress iaOut) throws ClassNotFoundException, IOException
	{
		last = Math.min(last, pList.size()-1);
		for(int i=first; i <= last; i++){
			sendDatagramPacket(pList.get(i), dgsOut, iaOut);
			lastSentAtPosition.set(i%8, i);
		}
		say("Last sent at position: " + lastSentAtPosition.toString());
		return last-first+1;
	}
	
	public static void sendDatagramPacket(packet p, DatagramSocket dgs, InetAddress ia) throws IOException, ClassNotFoundException{
		byte[] sendBuf = makeByteArrayFromPacket(p);
		seqnumlog.println(p.getSeqNum());
		say("CLIENT: Just sent a packet with sequence number: " + p.getSeqNum() + " (Mod: " + p.getSeqNum()%8 + ")");
		DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, ia, 6000);
		dgs.send(packet);
	}
	
	public static packet receiveDatagramAndConvert() throws IOException, ClassNotFoundException{
		byte[] recBuf = new byte[1024];
		DatagramPacket recpacket = new DatagramPacket(recBuf, recBuf.length);
		
		dgsIn.receive(recpacket);
		
		ByteArrayInputStream inSt = new ByteArrayInputStream(recBuf);
		ObjectInputStream oinSt = new ObjectInputStream(inSt);        
		packet p = (packet) oinSt.readObject();
		acklog.println(p.getSeqNum());
		say("CLIENT: Just received a packet with sequence number: " + p.getSeqNum() + " (Mod: " + p.getSeqNum()%8 + ")");
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
		return p;
	}
	
 	public static String readFileContentsFrom(String fileName) throws Exception {
 		//Read the file and get its contents
 		Scanner inputScanner = new Scanner(new File(fileName));
 		String content = inputScanner.useDelimiter("\\Z").next();
 		inputScanner.close();
 		
 		return content;
 	}


	public static ArrayList<packet> createAllPacketsFor(String content, ArrayList<packet> packetsList){
		int localSequenceNumber = 0;
		while (content.length() > 0){
			if (content.length() > 16){
				packet newPacket = new packet(1, localSequenceNumber%8, 16, content.substring(0, 16));
				content = content.substring(16, content.length());
				packetsList.add(newPacket);
			}
			else{
				packet newPacket = new packet(1, localSequenceNumber%8, content.length(), content);
				content = "";
				packetsList.add(newPacket);
			}
			localSequenceNumber++;
		}
		
		return packetsList;
	}
	
	public static void initializeSends(){
		for(int i = 0; i <= windowSize; i++){
			lastSentAtPosition.add(i);
		}
		
	}
	
	public static void setCommandLineVariables(String args[]){
		if (!eclipseMode){
			emulatorName = args[0];
			sendToEmulator = Integer.parseInt(args[1]);
			receiveFromEmulator = Integer.parseInt(args[2]);
			fileName = args[3];
		}
	}
}