import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrderHistory {

	List<ClientOrder> orders = new ArrayList<>();

	public synchronized void addOrder(ClientOrder order) {
		orders.add(order);
		order.orderID = orders.size();
	}

	public List<ClientOrder> searchOrdersByUser(String user) {
		return orders.stream().filter(order -> order.userName.equals(user)).collect(Collectors.toList());
	}

	public ClientOrder searchOrdersById(int id) {
		List<ClientOrder> result = orders.stream().filter(order -> order.orderID == id).collect(Collectors.toList());
		return (result.isEmpty() ? null : result.get(0));
	}

	public synchronized ClientOrder cancelOrderByID(int id) {
		if (id < 1 || id > orders.size() || !orders.get(id - 1).isActive)
			return null;
		orders.get(id - 1).isActive = false;
		return orders.get(id - 1);
	}

	public static void main(String[] args) {
		OrderHistory oh = new OrderHistory();

		oh.addOrder(new ClientOrder("Lu", "markers", 10));
		oh.addOrder(new ClientOrder("Lu", "crayons", 10));
		oh.addOrder(new ClientOrder("Lu", "toys", 10));
		oh.addOrder(new ClientOrder("Cinda", "toys", 120));
		oh.addOrder(new ClientOrder("Lu", "goldfish", 12));
		oh.addOrder(new ClientOrder("Cinda", "hugs", 10));

		oh.cancelOrderByID(2);

		System.out.println(oh.searchOrdersByUser("Lu").toString().replace(',', '\n'));
		System.out.println(oh.searchOrdersByUser("Cinda"));
	}

}
