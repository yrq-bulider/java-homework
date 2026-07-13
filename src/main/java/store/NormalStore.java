package store;

import store.lsm.LSMTree;
import util.Patterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private PersistentStore persistentStore;
    private boolean transientMode;
    private LSMTree lsmTree;
    private boolean lsmMode;

    public NormalStore() {}

    public NormalStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public NormalStore(LSMTree lsmTree) {
        this.lsmTree = lsmTree;
        this.lsmMode = true;
    }

    public void attachPersistentStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public void setTransient(boolean t) {
        this.transientMode = t;
    }

    public boolean isLsmMode() { return lsmMode; }
    public LSMTree lsmTree() { return lsmTree; }

    public void set(String key, String value, long ttlSeconds) {
        if (lsmMode) { lsmTree.set(key, value, ttlSeconds); return; }
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null && !transientMode) {
            try { persistentStore.appendSet(key, value, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        data.put(key, new Entry(key, value, expireAt, now));
    }

    public String get(String key) {
        if (lsmMode) return lsmTree.get(key);
        Entry e = data.get(key);
        if (e == null) return null;
        if (e.isExpired(System.currentTimeMillis())) {
            data.remove(key, e);
            return null;
        }
        return e.value();
    }

    public boolean del(String key) {
        if (lsmMode) return lsmTree.del(key);
        Entry prev = data.remove(key);
        if (prev != null && persistentStore != null && !transientMode) {
            try { persistentStore.appendDel(key); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        return prev != null;
    }

    public int mset(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        if (lsmMode) {
            int n = 0;
            for (int i = 0; i < kvs.length; i += 2) {
                lsmTree.set(kvs[i], kvs[i+1], ttlSeconds);
                n++;
            }
            return n;
        }
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null && !transientMode) {
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
        if (lsmMode) {
            int n = 0;
            for (String k : keys) if (lsmTree.del(k)) n++;
            return n;
        }
        if (persistentStore != null && !transientMode) {
            try { persistentStore.appendMdel(keys); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        int n = 0;
        for (String k : keys) if (delMemoryOnly(k)) n++;
        return n;
    }

    /** Batch update: only updates keys that already exist, returns count of actually updated keys. */
    public int mupd(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        if (lsmMode) {
            int n = 0;
            for (int i = 0; i < kvs.length; i += 2) {
                if (lsmTree.exists(kvs[i])) { lsmTree.set(kvs[i], kvs[i+1], ttlSeconds); n++; }
            }
            return n;
        }
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null && !transientMode) {
            try { persistentStore.appendMupd(kvs, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        int n = 0;
        for (int i = 0; i < kvs.length; i += 2) {
            if (data.containsKey(kvs[i])) {
                data.put(kvs[i], new Entry(kvs[i], kvs[i + 1], expireAt, now));
                n++;
            }
        }
        return n;
    }

    private boolean delMemoryOnly(String key) {
        Entry prev = data.remove(key);
        return prev != null;
    }

    public void flush() {
        if (lsmMode) { lsmTree.flush(); return; }
        if (persistentStore != null && !transientMode) {
            try { persistentStore.appendFlush(); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        data.clear();
    }

    public List<String> keys(String pattern) {
        if (lsmMode) return lsmTree.keys(pattern);
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
        if (lsmMode) return lsmTree.exists(key);
        return get(key) != null;
    }

    public int size() {
        if (lsmMode) return lsmTree.size();
        return data.size();
    }
}