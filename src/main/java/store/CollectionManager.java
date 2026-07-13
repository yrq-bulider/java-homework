package store;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class CollectionManager {
    private final NormalStore store;

    public CollectionManager(NormalStore store) {
        this.store = store;
    }

    public Collection collection(String name) {
        return new Collection(name, store);
    }

    public List<String> listCollections() {
        TreeSet<String> set = new TreeSet<>();
        for (String k : store.keys("*")) {
            int colon = k.indexOf(':');
            if (colon > 0) set.add(k.substring(0, colon));
        }
        return new ArrayList<>(set);
    }
}
