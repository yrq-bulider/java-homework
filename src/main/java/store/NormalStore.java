package store;

import util.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();

    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
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
        return prev != null;
    }

    /** Returns number of key/value pairs successfully inserted.
     *  Expects kvs to have even length; odd length → 0. */
    public int mset(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        int n = 0;
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        for (int i = 0; i < kvs.length; i += 2) {
            data.put(kvs[i], new Entry(kvs[i], kvs[i+1], expireAt, now));
            n++;
        }
        return n;
    }

    public int mdel(String[] keys) {
        if (keys == null) return 0;
        int n = 0;
        for (String k : keys) if (del(k)) n++;
        return n;
    }

    public void flush() {
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