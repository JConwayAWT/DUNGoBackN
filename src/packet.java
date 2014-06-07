public class packet implements java.io.Serializable
{
	private int type;
	private int seqnum;    // sequence number
	private int length;    // number of characters carried in data field 
	private String data;   // should be 0 for ACK packets  
    
	public packet(int t, int s, int l, String d){
	    type = t;
	    seqnum = s;
	    length = l;
	    data = d;
	}
	
	public int getType(){
		/**0 for ACK,
		 * 1 for data,
		 * 2 for end of transmission FROM THE SERVER TO THE CLIENT,
		 * 3 for end of transmission FROM THE CLIENT TO THE SERVER.
		 */
	    return type;
	}
	
	public int getSeqNum(){
	     return seqnum;   
	}
	
	public int getLength(){
	     return length;   
	}
	
	public String getData(){
	     return data;   
	}
	
	public void printContents(){
	     System.out.println("type: " + type + "  seqnum: " + seqnum + " length: " + length);
	     System.out.println("data: " + data);
	     System.out.println();
	}
	
}
