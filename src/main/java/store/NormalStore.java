package store;

import util.Patterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private PersistentStore persistentStore;

    public NormalStore() {}

    public NormalStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public void attachPersistentStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null) {
            try { persistentStore.appendSet(key, value, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        data.put(key, new Entry(key, value, expireAt, now));
    }

    public String get(String key) {
        Entry e = data.get(key);
        if (e == null) return null;
        if (e.isExpired(System.currentTimeMillis())) {
            data.remove(key, e);
            return null;
        }
        return e.value();
    }

    public boolean del(String key) {
        Entry prev = data.remove(key);
        if (prev != null && persistentStore != null) {
            try { persistentStore.appendDel(key); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        return prev != null;
    }

    public int mset(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null) {
            try { persistentStore.appendMset(kvs, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        int n = 0;
        for (int i = 0; i < kvs.length; i += 2) {
            data.put(kvs[i], new Entry(kvs[i], kvs[i+1], expireAt, now));
            n++;
        }
        return n;
    }

    public int mdel(String[] keys) {
        if (keys == null) return 0;
        if (persistentStore != null) {
            try { persistentStore.appendMdel(keys); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        int n = 0;
        for (String k : keys) if (delMemoryOnly(k)) n++;
        return n;
    }

    private boolean delMemoryOnly(String key) {
        Entry prev = data.remove(key);
        return prev != null;
    }

    public void flush() {
        if (persistentStore != null) {
            try { persistentStore.appendFlush(); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        data.clear();
    }

    public List<String> keys(String pattern) {
        List<String> result = new ArrayList<>();
        String p = pattern == null || pattern.isEmpty() ? "*" : pattern;
        for (String k : data.keySet()) {
            Entry e = data.get(k);
            if (e == null) continue;
            if (e.isExpired(System.currentTimeMillis())) {
                data.remove(k, e);
                continue;
            }
            if (Patterns.matches(p, k)) result.add(k);
        }
        return result;
    }

    public boolean exists(String key) {
        return get(key) != null;
    }

    public int size() {
        return data.size();
    }
}