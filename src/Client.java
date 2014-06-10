import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

class client implements Runnable{
	//Set a bunch of class variables.  Maybe too many class variables...
	public static int windowSize = 7;
	public static Boolean eclipseMode = false;
	public static String emulatorName;
	public static int sendToEmulator;
	public static int receiveFromEmulator;
	public static String fileName;
	public static PrintWriter seqnumlog;
	public static PrintWriter acklog;
	public static DatagramSocket dgsIn;
	public static ArrayList<Integer> lastSentAtPosition;
	public static Boolean printDots = true;
	
	public static void main(String args[]) throws Exception{
		setCommandLineVariables(args);
		
		//An eight-member list keeping track of the last true packet number sent at each modulo position
		lastSentAtPosition = new ArrayList<Integer>(windowSize+1);
		initializeSends();
		
		//Initialize the sending socket
		DatagramSocket dgsOut = new DatagramSocket();
		InetAddress iaOut = InetAddress.getByName(emulatorName);
		dgsOut.connect(iaOut, sendToEmulator);
		
		//Initialize the receiving socket
		dgsIn = new DatagramSocket(receiveFromEmulator);
		
		//Initalize the output files
		seqnumlog = new PrintWriter("seqnum.log","UTF-8");
		acklog = new PrintWriter("ack.log","UTF-8");
		
		//Read the file, turn it into an array of pre-packaged packets so we
		//only ever have to make them once
		String content = readFileContentsFrom(fileName);
		ArrayList<packet> packetsList = new ArrayList<packet>();
		packetsList = createAllPacketsFor(content, packetsList);
		int totalPackets = packetsList.size();
		
		//Fire off the first windowSize packets...
		for(int i = 0; i < windowSize; i++){
			if(i < totalPackets){ //but only if we have windowSize packets
				sendDatagramPacket(packetsList.get(i), dgsOut, iaOut);
			}
		} 
		
		//Set some variables used through the upcoming 'primary' loop of the program
		dgsIn.setSoTimeout(800);
		int timesToListen = Math.min(windowSize, totalPackets); //It's more efficient in some cases if you know how long you should listen
		int ackNumber = 0;
		int startIndex = 0; //When we send out the next packet(s), start with this one.
		System.out.print("Still processing...");
		
		client c = new client();
		Thread ct = new Thread(c);
		ct.start();
		
		while (startIndex < totalPackets){
			try{
				for(int i = 0; i < timesToListen; i++){
					//Collect any incoming acks and record their sequence numbers
					ackNumber = receiveDatagramAndConvert().getSeqNum();
					
				}
				//These two lines find the "true" packet number to send next via our array maintaining the last true packet number
				//for any given modulus of the window size.
				//The first line is pretty tortured because, in Java, -2%8 -> -2.  Yuck.
				int indexer = ((((ackNumber - 1) % (windowSize+1)) + (windowSize+1)) % (windowSize+1));
				startIndex = lastSentAtPosition.get(indexer)+1;
				
				//Send as many packets as we're allowed; keep track of how many we're actually sending
				timesToListen = sendAllPacketsInclusiveFromXtoY(startIndex, startIndex+windowSize-1, packetsList, dgsOut, iaOut);
			}
			catch(SocketTimeoutException e){
				//If we timed out, clearly we should stop listening.
				timesToListen = 0;
			}
		}
		
		//We want to send this separately to guarantee that all data packets have gotten through first.
		sendEndOfTransmission(dgsOut, iaOut, totalPackets);
		
		//Close files
		acklog.close();
		seqnumlog.close();
		
		printDots = false;
	}
	
	public static void say(String s){
		//Java is so verbose
		System.out.println(s);
	}
	
	/***
	 * Creates the EOT packet, sends it, and listens for the response.
	 * @param dgsOut
	 * @param iaOut
	 * @param totalPackets
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static void sendEndOfTransmission(DatagramSocket dgsOut, InetAddress iaOut, int totalPackets
			) throws UnknownHostException, IOException, ClassNotFoundException{
			packet p = new packet(3, totalPackets%(windowSize+1), 0, "");
			sendDatagramPacket(p, dgsOut, iaOut);
			packet pIn = receiveDatagramAndConvert();
	}
	
	/***
	 * Sends packets numbered first to last, inclusively, but does not go over the total length of the packet list.
	 * This also maintains/updates the array of most recent true packet numbers sent for each modulus of the window size.
	 * @param first
	 * @param last
	 * @param pList
	 * @param dgsOut
	 * @param iaOut
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public static int sendAllPacketsInclusiveFromXtoY(int first, int last, ArrayList<packet> pList, 
	DatagramSocket dgsOut, InetAddress iaOut) throws ClassNotFoundException, IOException
	{
		last = Math.min(last, pList.size()-1);
		for(int i=first; i <= last; i++){
			sendDatagramPacket(pList.get(i), dgsOut, iaOut);
			lastSentAtPosition.set(i%(windowSize+1), i);
		}
		return last-first+1;
	}
	
	/***
	 * Sends a packet and prints the sequence number to the file.
	 * @param p
	 * @param dgs
	 * @param ia
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static void sendDatagramPacket(packet p, DatagramSocket dgs, InetAddress ia) throws IOException, ClassNotFoundException{
		byte[] sendBuf = makeByteArrayFromPacket(p);
		seqnumlog.println(p.getSeqNum());
		//say("CLIENT: Just sent a packet with sequence number: " + p.getSeqNum());
		DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, ia, sendToEmulator);
		dgs.send(packet);
	}
	
	/***
	 * Receives a datagram, converts it to a packet, records the ack number in a file, and returns the packet.
	 * NOTE: This is a blocking function.
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static packet receiveDatagramAndConvert() throws IOException, ClassNotFoundException{
		byte[] recBuf = new byte[1024];
		DatagramPacket recpacket = new DatagramPacket(recBuf, recBuf.length);
		
		dgsIn.receive(recpacket);
		
		ByteArrayInputStream inSt = new ByteArrayInputStream(recBuf);
		ObjectInputStream oinSt = new ObjectInputStream(inSt);
		packet p = (packet) oinSt.readObject();
		acklog.println(p.getSeqNum());
		//say("CLIENT: Just received a packet with sequence number: " + p.getSeqNum());
		return p;
	}
	
	/***
	 * Takes a packet and turns it into a byte array
	 * @param p
	 * @return
	 * @throws IOException
	 */
	public static byte[] makeByteArrayFromPacket(packet p) throws IOException{
		ByteArrayOutputStream oSt = new ByteArrayOutputStream();
		ObjectOutputStream ooSt = new ObjectOutputStream(oSt);
		ooSt.writeObject(p);
		ooSt.close();
		byte[] sendBuf = oSt.toByteArray();
		return sendBuf;
	}
	
	/***
	 * Takes a byte array and turns it into a packet
	 * @param byteArray
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static packet makePacketFromByteArray(byte[] byteArray) throws IOException, ClassNotFoundException{
		packet p;
		ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);
		ObjectInput in = new ObjectInputStream(bais);
		p = (packet) in.readObject();
		return p;
	}
	
	/***
	 * Takes a file and reads the contents into a string.
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
 	public static String readFileContentsFrom(String fileName) throws Exception {
 		//Read the file and get its contents
 		Scanner inputScanner = new Scanner(new File(fileName));
 		String content = inputScanner.useDelimiter("\\Z").next();
 		inputScanner.close();
 		
 		return content;
 	}

 	/***
 	 * Create a list of packets for the given content and store it so that you never have to regenerate a packet,
 	 * just fish them out of memory.  This does NOT include the end of transmission packet.
 	 * @param content
 	 * @param packetsList
 	 * @return
 	 */
	public static ArrayList<packet> createAllPacketsFor(String content, ArrayList<packet> packetsList){
		int localSequenceNumber = 0;
		while (content.length() > 0){
			if (content.length() > 16){
				packet newPacket = new packet(1, localSequenceNumber%(windowSize+1), 16, content.substring(0, 16));
				content = content.substring(16, content.length());
				packetsList.add(newPacket);
			}
			else{
				packet newPacket = new packet(1, localSequenceNumber%(windowSize+1), content.length(), content);
				content = "";
				packetsList.add(newPacket);
			}
			localSequenceNumber++;
		}
		
		return packetsList;
	}
	
	/***
	 * Starts off the last packet number tracking array as [0, 1, 2, 3, ...]
	 */
	public static void initializeSends(){
		for(int i = 0; i <= windowSize; i++){
			lastSentAtPosition.add(i);
		}
		
	}
	
	/***
	 * Parses and sets the command line variables.
	 * @param args
	 */
	public static void setCommandLineVariables(String args[]){
		if (eclipseMode){
			emulatorName = "localhost";
			sendToEmulator = 6000;
			receiveFromEmulator = 6001;
			fileName = "inputFile.txt";
		}
		else{
			emulatorName = args[0];
			sendToEmulator = Integer.parseInt(args[1]);
			receiveFromEmulator = Integer.parseInt(args[2]);
			fileName = args[3];
		}
	}

	@Override
	public void run() {
		while(printDots){
			System.out.print(".");
			try {
				Thread.currentThread();
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		say(".");
		say("CLIENT: Client closed.");
		
	}
}