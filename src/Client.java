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
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

class Client implements Runnable{
	public static int sequenceNumber = 0;
	
	public static void main(String args[]) throws Exception{
		PrintWriter seqnumlog = new PrintWriter("seqnum.log","UTF-8");
		PrintWriter acklog = new PrintWriter("ack.log","UTF-8");
		
		String serverAddress = "localhost";
//		Integer finalPort = 6000 + (int)(Math.random() * ((60000 - 6000) + 1));
		Integer finalPort = 6000;
		Integer negotiationPort = 6000;
		String fileName = "inputFile.txt";
		
		String content = readFileContentsFrom(fileName);
		ArrayList<packet> packetsList = new ArrayList<packet>();
		packetsList = createAllPacketsFor(content, packetsList);
		int totalPackets = packetsList.size();
		
		while(sequenceNumber < totalPackets){
			sendDataPacketFull(packetsList.get(sequenceNumber), serverAddress, finalPort, seqnumlog, acklog);
		}
		
		sendEndOfTransmission(serverAddress, finalPort, seqnumlog, acklog);
		acklog.close();
		seqnumlog.close();
		
	}
	
	
	public static void sendEndOfTransmission(String serverAddress, Integer finalPort, PrintWriter seqnumlog, PrintWriter acklog
		) throws UnknownHostException, IOException, ClassNotFoundException{
		packet p = new packet(3, sequenceNumber, 0, "");
		
		Socket exchangeSocket = new Socket(serverAddress, finalPort);
		DataOutputStream serverOutput = new DataOutputStream(exchangeSocket.getOutputStream());
		DataInputStream dataIn = new DataInputStream(exchangeSocket.getInputStream());
		
		byte[] packetAsBytes = makeByteArrayFromPacket(p);
		serverOutput.write(packetAsBytes);
		serverOutput.flush();
		seqnumlog.println(p.getSeqNum());
		
		
		byte[] message = new byte[1024];
		dataIn.read(message);
		packet pIn = makePacketFromByteArray(message);
		acklog.println(pIn.getSeqNum());
		
		exchangeSocket.close();
	}
	
	public static void sendDataPacketFull(
	packet p, String serverAddress, Integer finalPort, PrintWriter seqnumlog, PrintWriter acklog)
	throws UnknownHostException, IOException, ClassNotFoundException{
		Socket exchangeSocket = new Socket(serverAddress, finalPort);
		DataOutputStream serverOutput = new DataOutputStream(exchangeSocket.getOutputStream());
		DataInputStream dataIn = new DataInputStream(exchangeSocket.getInputStream());
		
		byte[] packetAsBytes = makeByteArrayFromPacket(p);
		serverOutput.write(packetAsBytes);
		serverOutput.flush();
		seqnumlog.println(p.getSeqNum());
		
		byte[] message = new byte[1024];
		dataIn.read(message);
		packet pIn = makePacketFromByteArray(message);
		acklog.println(pIn.getSeqNum());
		
		sequenceNumber = pIn.getSeqNum() + 1;
		
		exchangeSocket.close();
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
}