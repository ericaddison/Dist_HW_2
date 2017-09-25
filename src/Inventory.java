import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class Inventory {

	private HashMap<String, Integer> inventory;

	public Inventory(String inputFile) {
		inventory = new HashMap<>(10);
		parseFile(inputFile);
	}

	public Set<String> getItemNames() {
		return inventory.keySet();
	}

	public int getItemCount(String item) {
		if (inventory.containsKey(item))
			return inventory.get(item);
		return -1;
	}

	public synchronized void removeItem(String item, int amount) {
		if (inventory.containsKey(item) && inventory.get(item) > 0)
			inventory.put(item, inventory.get(item) - amount);
	}

	public synchronized void addItem(String item, int amount) {
		if (inventory.containsKey(item))
			inventory.put(item, inventory.get(item) + amount);
		else
			inventory.put(item, amount);
	}

	private void parseFile(String inputFile) {

		try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] tokens = line.split(" ");
				inventory.put(tokens[0], Integer.parseInt(tokens[1]));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Inventory inv = new Inventory("input/input.txt");
		System.out.println(inv.getItemNames());
		System.out.println(inv.getItemCount("ps4"));
	}

}
