package store;

import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();

    /** ttlSeconds = 0 means never expires; if > 0 then expireAt = now + ttlSeconds*1000. */
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

    public int size() { return data.size(); }
}
