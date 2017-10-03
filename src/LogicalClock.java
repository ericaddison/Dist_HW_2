
public class LogicalClock {

	private int c=0;
	
	public LogicalClock(int c) {
		this.c = c;
	}
	
	public void increment(){
		c++;
	}
	
	public int value(){
		return c;
	}
	
}
