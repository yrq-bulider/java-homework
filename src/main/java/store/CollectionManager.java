package store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class CollectionManager {
    /** Special key holding JSON-serialized list of declared collection names. */
    public static final String META_KEY = "__collections__";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>(){}.getType();

    private final NormalStore store;

    public CollectionManager(NormalStore store) {
        this.store = store;
    }

    public Collection collection(String name) {
        return new Collection(name, store);
    }

    /** Names declared via CREATE or that currently hold at least one key. */
    public List<String> listCollections() {
        TreeSet<String> set = new TreeSet<>(loadDeclared());
        for (String k : store.keys("*")) {
            int colon = k.indexOf(':');
            if (colon > 0) set.add(k.substring(0, colon));
        }
        return new ArrayList<>(set);
    }

    /** Idempotent: declare a collection so it shows up in LIST even when empty. */
    public void createCollection(String name) {
        if (name == null || name.isEmpty()) return;
        TreeSet<String> declared = new TreeSet<>(loadDeclared());
        if (declared.add(name)) saveDeclared(new ArrayList<>(declared));
    }

    /** Remove declaration and delete every key in this collection. */
    public int dropCollection(String name) {
        if (name == null || name.isEmpty()) return 0;
        TreeSet<String> declared = new TreeSet<>(loadDeclared());
        declared.remove(name);
        saveDeclared(new ArrayList<>(declared));
        List<String> keys = store.keys(name + ":*");
        int n = 0;
        for (String k : keys) if (store.del(k)) n++;
        return n;
    }

    private List<String> loadDeclared() {
        String raw = store.get(META_KEY);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        try {
            List<String> parsed = GSON.fromJson(raw, LIST_TYPE);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveDeclared(List<String> names) {
        store.set(META_KEY, GSON.toJson(names), 0L);
    }
}