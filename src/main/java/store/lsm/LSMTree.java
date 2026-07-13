package store.lsm;

import store.Entry;
import store.PersistentStore;
import util.Logger;
import util.Patterns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * LSM-Tree storage engine.
 *
 * Architecture (simplified):
 *   Write path: WAL append → Active MemTable insert
 *   Flush:      When MemTable reaches threshold → write as SSTable (Level 0)
 *   Read path:  Active MemTable → Immutable MemTables → SSTables (L0 → L1 → ...)
 *   Compaction: Size-tiered — merge smaller SSTables into larger ones
 *
 * Level structure:
 *   Level 0: SSTables from MemTable flushes (may overlap in key ranges)
 *   Level 1+: Non-overlapping key ranges within a level, each level ~10x larger
 */
public class LSMTree implements AutoCloseable {

    private static final long MEMTABLE_FLUSH_BYTES = 16L * 1024 * 1024; // 16 MB
    private static final long COMPACTION_INTERVAL_MIN = 5;

    private final Path dataDir;
    private final PersistentStore wal;
    private final ExecutorService flushPool = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService compactionScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Active mutable MemTable
    private volatile MemTable activeMemTable = new MemTable();
    // Immutable MemTables waiting to be flushed
    private final List<MemTable> immutableMemTables = new ArrayList<>();
    // SSTable levels: Level 0..N
    private final List<List<SSTableReader>> levels = new ArrayList<>();

    private final Object memTableLock = new Object();
    private int sstableSeq;

    public LSMTree(Path dataDir, PersistentStore wal) throws IOException {
        this.dataDir = dataDir;
        this.wal = wal;
        Files.createDirectories(dataDir);
        levels.add(new ArrayList<>()); // Level 0
    }

    // ---- Write Path ----

    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        // WAL is handled by caller (NormalStore delegates to PersistentStore)
        // MemTable insert
        activeMemTable.put(key, new Entry(key, value, expireAt, now));
        maybeFlush();
    }

    public boolean del(String key) {
        // Tombstone: set value to empty string with 1s TTL as marker
        long now = System.currentTimeMillis();
        activeMemTable.put(key, new Entry(key, "", now + 1000, now));
        maybeFlush();
        return true; // approximate
    }

    public void flush() {
        activeMemTable.clear();
        synchronized (memTableLock) {
            for (MemTable im : immutableMemTables) im.clear();
        }
        for (List<SSTableReader> level : levels) {
            for (SSTableReader r : level) {
                try { r.close(); } catch (IOException ignored) {}
            }
        }
        levels.clear();
        levels.add(new ArrayList<>());
    }

    private void maybeFlush() {
        if (activeMemTable.byteSize() >= MEMTABLE_FLUSH_BYTES) {
            synchronized (memTableLock) {
                immutableMemTables.add(activeMemTable);
                activeMemTable = new MemTable();
            }
            flushPool.submit(() -> flushImmutableMemTables());
        }
    }

    private void flushImmutableMemTables() {
        synchronized (memTableLock) {
            for (int idx = 0; idx < immutableMemTables.size(); idx++) {
                MemTable im = immutableMemTables.get(idx);
                if (im.isEmpty()) { immutableMemTables.remove(idx); idx--; continue; }
                try {
                    int seq = sstableSeq++;
                    Path sstPath = dataDir.resolve(String.format("sst-%04d.sst", seq));
                    try (SSTableWriter writer = new SSTableWriter(sstPath, im.size())) {
                        writer.writeAll(im.entryMap().entrySet());
                    }
                    // GZIP-compress for archival
                    SSTableWriter.compressToGzip(sstPath);
                    Path gzPath = sstPath.resolveSibling(sstPath.getFileName() + ".gz");
                    // Register in Level 0
                    SSTableReader reader = new SSTableReader(gzPath);
                    synchronized (levels) {
                        levels.get(0).add(reader);
                    }
                    Logger.info("Flushed SSTable: " + gzPath.getFileName() + " (" + im.size() + " entries)");
                    immutableMemTables.remove(idx);
                    idx--;
                } catch (Exception e) {
                    Logger.warn("flush failed: " + e.getMessage());
                }
            }
        }
    }

    // ---- Read Path ----

    public String get(String key) {
        long now = System.currentTimeMillis();

        // 1. Check active MemTable
        Entry e = activeMemTable.get(key);
        if (e != null) {
            if (e.isExpired(now)) { activeMemTable.delete(key); return null; }
            return e.value();
        }

        // 2. Check immutable MemTables
        synchronized (memTableLock) {
            for (int i = immutableMemTables.size() - 1; i >= 0; i--) {
                e = immutableMemTables.get(i).get(key);
                if (e != null) {
                    if (e.isExpired(now)) return null;
                    return e.value();
                }
            }
        }

        // 3. Check SSTables (Level 0 → Level N)
        synchronized (levels) {
            for (List<SSTableReader> level : levels) {
                // Level 0: check all files (may overlap)
                // Higher levels: only one file can contain the key (non-overlapping)
                for (int i = level.size() - 1; i >= 0; i--) {
                    SSTableReader r = level.get(i);
                    if (r.bloomFilter().mightContain(key)) {
                        try {
                            String val = r.get(key);
                            if (val != null) return val;
                        } catch (IOException ex) {
                            Logger.warn("sstable read error: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        return null;
    }

    public boolean exists(String key) {
        return get(key) != null;
    }

    public List<String> keys(String pattern) {
        String p = pattern == null || pattern.isEmpty() ? "*" : pattern;
        List<String> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Scan active MemTable
        for (Map.Entry<String, Entry> me : activeMemTable.entryMap().entrySet()) {
            if (!me.getValue().isExpired(now) && Patterns.matches(p, me.getKey()))
                result.add(me.getKey());
        }

        // Scan SSTables (full scan for keys with pattern)
        synchronized (levels) {
            for (List<SSTableReader> level : levels) {
                for (SSTableReader r : level) {
                    try {
                        for (SSTableReader.EntryData ed : r.readAll()) {
                            if (Patterns.matches(p, ed.key) && !result.contains(ed.key))
                                result.add(ed.key);
                        }
                    } catch (IOException ex) {
                        Logger.warn("keys scan error: " + ex.getMessage());
                    }
                }
            }
        }

        return result;
    }

    public int size() {
        int s = activeMemTable.size();
        synchronized (memTableLock) {
            for (MemTable im : immutableMemTables) s += im.size();
        }
        return s;
    }

    /** Force flush of MemTable (for shutdown). */
    public void forceFlush() {
        synchronized (memTableLock) {
            if (!activeMemTable.isEmpty()) {
                immutableMemTables.add(activeMemTable);
                activeMemTable = new MemTable();
            }
        }
        flushImmutableMemTables();
        flushPool.shutdown();
        try { flushPool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    // ---- Compaction ----

    public void startCompaction() {
        compactionScheduler.scheduleAtFixedRate(
                this::safeCompact, 30, COMPACTION_INTERVAL_MIN, TimeUnit.MINUTES);
    }

    private void safeCompact() {
        try { compact(); }
        catch (Exception e) { Logger.warn("compaction failed: " + e.getMessage()); }
    }

    /** Size-tiered compaction: merge Level 0 files into Level 1 when threshold exceeded. */
    public synchronized void compact() throws IOException {
        synchronized (levels) {
            if (levels.isEmpty() || levels.get(0).size() < 4) return; // need at least 4 files to compact

            List<SSTableReader> l0 = levels.get(0);
            // Pick files to merge (oldest first)
            List<SSTableReader> toMerge = new ArrayList<>(l0.subList(0, Math.min(l0.size(), 8)));
            l0.removeAll(toMerge);

            // Merge sort all entries
            TreeMap<String, SSTableReader.EntryData> merged = new TreeMap<>();
            for (SSTableReader r : toMerge) {
                for (SSTableReader.EntryData ed : r.readAll()) {
                    merged.put(ed.key, ed); // last write wins (newer key overwrites older)
                }
                r.close();
            }

            // Write merged SSTable
            int seq = sstableSeq++;
            Path sstPath = dataDir.resolve(String.format("sst-%04d.sst", seq));
            try (SSTableWriter writer = new SSTableWriter(sstPath, merged.size())) {
                for (Map.Entry<String, SSTableReader.EntryData> me : merged.entrySet()) {
                    writer.writeEntry(me.getKey(), me.getValue().toEntry());
                }
            }
            SSTableWriter.compressToGzip(sstPath);
            Path gzPath = sstPath.resolveSibling(sstPath.getFileName() + ".gz");

            // Add to Level 1 (creating if needed)
            if (levels.size() < 2) levels.add(new ArrayList<>());
            levels.get(1).add(new SSTableReader(gzPath));

            // Clean up old index files
            for (SSTableReader r : toMerge) {
                try {
                    String base = r.path().toString().replaceAll("\\.sst(\\.gz)?$", "");
                    Files.deleteIfExists(r.path());
                    Files.deleteIfExists(r.path().getFileSystem().getPath(base + ".sst.idx"));
                } catch (IOException ignored) {}
            }

            Logger.info("Compacted " + toMerge.size() + " files → " + gzPath.getFileName()
                    + " (" + merged.size() + " entries)");
        }
    }

    // ---- Recovery ----

    /** Load existing SSTables from data directory (for restart recovery). */
    public void loadExistingSSTables() throws IOException {
        List<Path> sstFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().matches("sst-\\d+\\.sst\\.gz"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());

        synchronized (levels) {
            for (Path p : sstFiles) {
                levels.get(0).add(new SSTableReader(p));
            }
        }
        Logger.info("Loaded " + sstFiles.size() + " existing SSTables");
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        compactionScheduler.shutdownNow();
        forceFlush();
        synchronized (levels) {
            for (List<SSTableReader> level : levels) {
                for (SSTableReader r : level) {
                    try { r.close(); } catch (IOException ignored) {}
                }
            }
        }
    }
}
