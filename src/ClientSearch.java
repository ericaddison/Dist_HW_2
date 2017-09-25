import java.io.Serializable;

public class ClientSearch implements Serializable {

	private static final long serialVersionUID = 1L;
	public String username;

	public ClientSearch(String username) {
		super();
		this.username = username;
	}

}
