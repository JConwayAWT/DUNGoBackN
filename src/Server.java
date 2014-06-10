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

class server implements Runnable{
	//Set the class variables.
	public static Boolean eclipseMode = false;
	public static Integer expectedSequenceNumber = 0;
	public static String emulatorName;
	public static Integer receiveFromEmulator;
	public static Integer sendToEmulator;
	public static String fileName;
	public static PrintWriter arrivallog;
	public static PrintWriter fileWriter;
	public static Boolean printDots = true;
	
	public static void main(String args[]) throws Exception{
		//Parse the command line arguments
		setCommandLineVariables(args);
		
		//Boolean value that's flipped on EOT reception only.
		Boolean continueWorking = true;
		
		//Initialize the output file and arrival file
		fileWriter = new PrintWriter(fileName,"UTF-8");
		arrivallog = new PrintWriter("arrival.log","UTF-8");
		
		
		//Connect to the emulator; keep these variables around
		DatagramSocket dgsOut = new DatagramSocket();
		InetAddress iaOut = InetAddress.getByName(emulatorName);
		dgsOut.connect(iaOut, sendToEmulator);
		
		//Let things come in from the emulator; keep these variables around
		DatagramSocket dgsIn;
		dgsIn = new DatagramSocket(receiveFromEmulator);
		
		say("SERVER: Server listening.");
		
		System.out.print("Still processing...");
		server s = new server();
		Thread st = new Thread(s);
		st.start();
		
		
		//While we haven't gotten an EOT...
		while (continueWorking){
			//...listen for a datagram coming in...
			packet p = receiveDatagramAndConvert(dgsIn);
			
			if (p.getType() == 1){
				//..and if it's a data packet...
				if (p.getSeqNum() == (expectedSequenceNumber%8)){//...and it's the right data packet...
					//...Write the data to a file and send out the ack for it...
					fileWriter.print(p.getData());
					sendAckPacket(p, dgsOut, iaOut);
					expectedSequenceNumber++;
				}
				else{//...but if it's not the right packet...
					//...send out the packet we're expecting...
					sendExpectationPacket(dgsOut, iaOut);
				}
			}
			else{//...and if it's not a data packet, then it's an EOT packet...
				//...so stop working and send our own EOT packet.
				continueWorking = false;
				sendEndOfTransmissionPacket(p, dgsOut, iaOut);
			}
		}		
		
		//Close everything
		fileWriter.close();
		arrivallog.close();
		dgsIn.close();
		dgsOut.close();
		
		printDots = false;
	}
	
	public static void say(String s){
		//Java is so verbose
		System.out.println(s);
	}
	
	/***
	 * Receives a datagram, converts it to a packet, returns the packet.  Also writes the incoming sequence number to file.
	 * NOTE: This is a blocking function.
	 * @param dgsIn
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static packet receiveDatagramAndConvert(DatagramSocket dgsIn) throws IOException, ClassNotFoundException{
		byte[] recBuf = new byte[1024];
		DatagramPacket recpacket = new DatagramPacket(recBuf, recBuf.length);
		
		dgsIn.receive(recpacket);
		
		ByteArrayInputStream inSt = new ByteArrayInputStream(recBuf);
		ObjectInputStream oinSt = new ObjectInputStream(inSt);        
		packet p = (packet) oinSt.readObject();
		arrivallog.println(p.getSeqNum());
		return p;
	}
	
	/***
	 * Sends an ack packet for a given packet.
	 * @param p
	 * @param dgs
	 * @param ia
	 * @throws IOException
	 */
	public static void sendAckPacket(packet p, DatagramSocket dgs, InetAddress ia) throws IOException{
		packet pOut = new packet(0, (p.getSeqNum()+1)%8, 0, "");
		byte[] sendBuf = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(sendBuf, sendBuf.length, ia, sendToEmulator);
		dgs.send(dgPacket);
	}
	
	/***
	 * Sends an expectation packet when the wrong packet is received.
	 * @param dgsOut
	 * @param ia
	 * @throws IOException
	 */
	public static void sendExpectationPacket(DatagramSocket dgsOut, InetAddress ia) throws IOException{
		packet pOut = new packet(0, expectedSequenceNumber%8,0, "");
		byte[] packetAsBytes = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(packetAsBytes, packetAsBytes.length, ia, sendToEmulator);
		dgsOut.send(dgPacket);
	}
	
	/***
	 * Sends end of transmission packet at the end of operations.
	 * @param pIn
	 * @param dgsOut
	 * @param ia
	 * @throws IOException
	 */
	public static void sendEndOfTransmissionPacket(packet pIn, DatagramSocket dgsOut, InetAddress ia) throws IOException{
		packet pOut = new packet(2, (pIn.getSeqNum()+1)%8,0,"");
		byte [] packetAsBytes = makeByteArrayFromPacket(pOut);
		DatagramPacket dgPacket = new DatagramPacket(packetAsBytes, packetAsBytes.length, ia, sendToEmulator);
		dgsOut.send(dgPacket);
	}
	
	/***
	 * Takes a datagram packet and turns it into a packet.
	 * @param dgp
	 * @param buff
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static packet makePacketFromByteArray(DatagramPacket dgp, byte[] buff) throws IOException, ClassNotFoundException{
		packet p;
		ByteArrayInputStream bais = new ByteArrayInputStream(buff);
		ObjectInputStream oinSt = new ObjectInputStream(bais);
		p = (packet) oinSt.readObject();
		return p;
	}
	
	/***
	 * Takes a packet and turns it into a byte array.
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
	 * Sets command line variables
	 * @param args
	 */
	public static void setCommandLineVariables(String args[]){
		if (eclipseMode){
			emulatorName = "localhost";
			receiveFromEmulator = 6002;
			sendToEmulator = 6000;
			fileName = "received.txt";
		}
		else{
			emulatorName = args[0];
			receiveFromEmulator = Integer.parseInt(args[1]);
			sendToEmulator = Integer.parseInt(args[2]);
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