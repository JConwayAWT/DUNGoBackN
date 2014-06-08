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
	public static int nextUpToSend;
	
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
		} 
		
		nextUpToSend = 7;
		//now sequence number = 7
		//the sequence number of the last packet that was actually sent = 6
		//oldestSentButUnackedPacketNumber = 0
		//we want to grab packetsList(7) next no matter what
		//but if we get an ack of, say, 5, we need to send from 7 through 5-1+7=11 all at once
		
		dgsIn.setSoTimeout(800);
		while (oldestSentButUnackedPacketNumber < totalPackets){
			try{
				packet pIn = receiveDatagramAndConvert();
				int ackNumber = pIn.getSeqNum();
				say("Received a packet: ");
				say("*** ackNumber = " + ackNumber);
				say("*** oldestSentUnacked = " + oldestSentButUnackedPacketNumber);
				if (oldestSentButUnackedPacketNumber == ackNumber){
					//duplicate ack! resend everything they missed (up to but NOT INCLUDING nextUpToSend)
					say("***FAILURE BRANCH: ackNumber matches oldestSent already");
					int maximumAllowed = nextUpToSend - 1;
					nextUpToSend = ackNumber;
					sendAllPacketsInclusiveFromXtoY(nextUpToSend, maximumAllowed, packetsList, dgsOut, iaOut);
					nextUpToSend = maximumAllowed + 1;
					say("***Next up to send is now equal to " + nextUpToSend);
				}
				else{
					say("***SUCCESS BRANCH: ackNumber didn't match oldestSent already");
					oldestSentButUnackedPacketNumber = ackNumber;
					int maximumAllowed = ackNumber + windowSize - 1;
					sendAllPacketsInclusiveFromXtoY(nextUpToSend, maximumAllowed, packetsList, dgsOut, iaOut);
					nextUpToSend = maximumAllowed + 1;
					say("***Next up to send is now equal to " + nextUpToSend);
				}
				say("\n");
			}
			catch(SocketTimeoutException e){
				//deal with this later
			}
		}
		
		sendEndOfTransmission(dgsOut, iaOut, totalPackets);
		acklog.close();
		seqnumlog.close();
		
	}
	
	public static void say(String s){
		System.out.println(s);
	}
	
	public static void sendAllPacketsInclusiveFromXtoY(int first, int last, ArrayList<packet> pList, 
	DatagramSocket dgsOut, InetAddress iaOut) throws ClassNotFoundException, IOException
	{
		last = Math.min(last, 22);
		System.out.println("***Client now sending from " + first + " to " + last + " inclusive!");
		for(int i=first; i <= last; i++){
			sendDatagramPacket(pList.get(i), dgsOut, iaOut);
		}
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
		//System.out.println("Client just sent a packet: ");
		//p.printContents();
	}
	
	public static void printReceivedPacketToConsole(packet p){
		//System.out.println("Client just received a packet: ");
		//p.printContents();
	}
}