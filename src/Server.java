import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

class Server implements Runnable{
	public static Integer expectedSequenceNumber = 0;
	
	public static void main(String args[]) throws Exception{
		Boolean continueWorking = true;
		PrintWriter fileWriter = new PrintWriter("received.txt","UTF-8");
		PrintWriter arrivallog = new PrintWriter("arrival.log","UTF-8");
		
		ServerSocket exchangeSocket = new ServerSocket(6000);		
		
		
		
		while (continueWorking){
			Socket connectionSocket = exchangeSocket.accept();
			DataOutputStream dataOut = new DataOutputStream(connectionSocket.getOutputStream());
			DataInputStream dataIn = new DataInputStream(connectionSocket.getInputStream());
			
			byte[] message = new byte[1024];
			dataIn.read(message);
			packet p = makePacketFromByteArray(message);
			arrivallog.println(p.getSeqNum());
			
			if (p.getType() == 1){
				//it's a data packet
				if (p.getSeqNum() == expectedSequenceNumber){ //<--THIS RIGHT HERE IS THE TRUE CODE
					if (Math.random() > 0.5){ //<-- This "drops a packet" 10% of the time				
						fileWriter.print(p.getData());
						sendAckPacket(p, dataOut);
						expectedSequenceNumber++;
					}
					else{
						sendExpectationPacket(dataOut);
					}
				}
				else{
					sendExpectationPacket(dataOut);
				}
			}
			else{
				//it's an EOT packet
				continueWorking = false;
				sendEndOfTransmissionPacket(p, dataOut);
			}
		}		
		
		fileWriter.close();
		arrivallog.close();
		
		exchangeSocket.close();
		
	}
	
	public static void sendEndOfTransmissionPacket(packet pIn, DataOutputStream dataOut) throws IOException{
		packet pOut = new packet(2, pIn.getSeqNum(),0,"");
		
		byte [] packetAsBytes = makeByteArrayFromPacket(pOut);
		dataOut.write(packetAsBytes);
		dataOut.flush();
	}
	
	public static void sendExpectationPacket(DataOutputStream dataOut) throws IOException{
		packet pOut = new packet(0, expectedSequenceNumber-1,0, "");
		byte[] packetAsBytes = makeByteArrayFromPacket(pOut);
		dataOut.write(packetAsBytes);
		dataOut.flush();
	}
	
	public static void sendAckPacket(packet pIn, DataOutputStream dataOut) throws IOException{
		packet pOut = new packet(0, pIn.getSeqNum(),0,"");
		
		byte[] packetAsBytes = makeByteArrayFromPacket(pOut);
		dataOut.write(packetAsBytes);
		dataOut.flush();
	}
	
	public static packet makePacketFromByteArray(byte[] byteArray) throws IOException, ClassNotFoundException{
		packet p;
		ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);
		ObjectInput in = new ObjectInputStream(bais);
		p = (packet) in.readObject();
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

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
}