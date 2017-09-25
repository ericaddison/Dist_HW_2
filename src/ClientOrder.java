import java.io.Serializable;

public class ClientOrder implements Serializable {

	private static final long serialVersionUID = 1L;
	public String userName;
	public String productName;
	public int quantity;
	public boolean isActive;
	public int orderID;

	public ClientOrder(String un, String pn, int q) {
		userName = un;
		productName = pn;
		quantity = q;
		isActive = true;
	}

	public String toString() {
		return orderID + ": " + userName + ": " + quantity + " * " + productName + " ("
				+ (isActive ? "active" : "cancelled") + ")";
	}

}
