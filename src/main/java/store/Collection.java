package store;

import java.util.ArrayList;
import java.util.List;

public class Collection {
    private final String name;            // e.g. "user"
    private final NormalStore store;

    Collection(String name, NormalStore store) {
        this.name = name;
        this.store = store;
    }

    public String name() { return name; }

    public void set(String shortKey, String value, long ttlSeconds) {
        store.set(prefixed(shortKey), value, ttlSeconds);
    }

    public String get(String shortKey) {
        return store.get(prefixed(shortKey));
    }

    public boolean del(String shortKey) {
        return store.del(prefixed(shortKey));
    }

    public List<String> listKeys() {
        List<String> result = new ArrayList<>();
        for (String k : store.keys(name + ":*")) {
            result.add(k.substring(name.length() + 1));
        }
        return result;
    }

    private String prefixed(String shortKey) {
        return name + ":" + shortKey;
    }
}
