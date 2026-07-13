package store.lsm;

import store.Entry;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * A sorted, thread-safe in-memory table (MemTable).
 * Wraps {@link ConcurrentSkipListMap} for O(log n) operations with sorted iteration.
 */
public class MemTable {
    private final ConcurrentSkipListMap<String, Entry> data = new ConcurrentSkipListMap<>();
    private volatile long byteSize;

    public void put(String key, Entry entry) {
        Entry prev = data.put(key, entry);
        if (prev != null) byteSize -= estimateSize(key, prev);
        byteSize += estimateSize(key, entry);
    }

    public Entry get(String key) {
        return data.get(key);
    }

    public Entry delete(String key) {
        Entry prev = data.remove(key);
        if (prev != null) byteSize -= estimateSize(key, prev);
        return prev;
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public int size() {
        return data.size();
    }

    public long byteSize() {
        return byteSize;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    /** Returns the underlying sorted map for iteration / flushing. */
    public NavigableMap<String, Entry> entryMap() {
        return data;
    }

    /** Returns entries in [start, end) range. */
    public Iterable<Map.Entry<String, Entry>> range(String start, String end) {
        if (end == null) return data.tailMap(start).entrySet();
        return data.subMap(start, true, end, false).entrySet();
    }

    public void clear() {
        data.clear();
        byteSize = 0;
    }

    private long estimateSize(String key, Entry entry) {
        return key.length() * 2L + (entry.value() != null ? entry.value().length() * 2L : 0) + 32L;
    }
}
