
public class LogicalClock {

	private int c=0;
	
	public void increment(){
		c++;
	}
	
	public int value(){
		return c;
	}
	
}
