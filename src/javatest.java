public class javatest{
	public static void main(String args[]) throws InterruptedException{
		int x = 0;
		while (x < 20){
			x += 1;
			int y = 20;
			Thread.sleep(500);
			System.out.println(y);
		}
	}
}