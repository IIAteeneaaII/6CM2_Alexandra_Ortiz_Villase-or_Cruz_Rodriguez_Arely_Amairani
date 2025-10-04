import java.util.HashMap;
import java.util.Map;

public class Cart {
    // itemId -> cantidad
    private final Map<Integer, Integer> items = new HashMap<>();

    public synchronized void add(int itemId, int qty) {
        items.merge(itemId, qty, Integer::sum);
    }

    public synchronized void setQuantity(int itemId, int qty) {
        if (qty <= 0) items.remove(itemId);
        else items.put(itemId, qty);
    }

    public synchronized void remove(int itemId) {
        items.remove(itemId);
    }

    public synchronized Map<Integer, Integer> getItems() {
        return new HashMap<>(items);
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }
}
