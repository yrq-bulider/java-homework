package store.lsm;

import store.Entry;
import util.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads an SSTable file produced by {@link SSTableWriter}.
 *
 * Loading strategy:
 *   1. Read the .sst.idx index file → load sparse index + bloom filter into memory
 *   2. For point lookups: check bloom → binary search index → seek into .sst data file
 *   3. For full scan (compaction): read all entries sequentially (uncompressed .sst)
 */
public class SSTableReader implements AutoCloseable {

    private final Path dataPath;
    private final RandomAccessFile dataFile;
    private final List<IndexEntry> index;
    private final BloomFilter bloom;
    private final int entryCount;

    public SSTableReader(Path dataPath) throws IOException {
        this.dataPath = dataPath;
        String base = dataPath.toString().replaceAll("\\.sst(\\.gz)?$", "");
        Path idxPath = dataPath.getFileSystem().getPath(base + ".sst.idx");

        if (!Files.exists(idxPath)) {
            throw new IOException("index file not found: " + idxPath);
        }

        // Load index from .idx file
        try (DataInputStream idxIn = new DataInputStream(new BufferedInputStream(
                new FileInputStream(idxPath.toFile())))) {
            int magic = idxIn.readInt();
            if (magic != 0x49445831) throw new IOException("bad idx magic"); // "IDX1"
            int idxCount = idxIn.readInt();
            this.index = new ArrayList<>(idxCount);
            for (int i = 0; i < idxCount; i++) {
                int kLen = idxIn.readInt();
                byte[] kb = new byte[kLen];
                idxIn.readFully(kb);
                String key = new String(kb, StandardCharsets.UTF_8);
                long offset = idxIn.readLong();
                index.add(new IndexEntry(key, offset));
            }
            // Bloom filter
            int bfBits = idxIn.readInt();
            int bfHashes = idxIn.readInt();
            int bfBytes = idxIn.readInt();
            byte[] bfData = new byte[bfBytes];
            idxIn.readFully(bfData);
            this.bloom = new BloomFilter(bfBits, bfHashes, bfData);
        }

        // Infer entry count from index (each index entry covers ~64 entries)
        this.entryCount = index.size() * 64;

        // Open data file for random access
        this.dataFile = new RandomAccessFile(dataPath.toFile(), "r");
    }

    /** Point lookup by key. Returns value or null. */
    public String get(String key) throws IOException {
        if (!bloom.mightContain(key)) return null;

        // Binary search the sparse index
        int lo = 0, hi = index.size() - 1;
        int candidate = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            int cmp = index.get(mid).key.compareTo(key);
            if (cmp <= 0) { candidate = mid; lo = mid + 1; }
            else { hi = mid - 1; }
        }
        if (candidate < 0) return null;

        // Seek to candidate index entry and linear scan forward
        long offset = index.get(candidate).offset;
        dataFile.seek(offset);

        long now = System.currentTimeMillis();
        for (int i = 0; i < 128; i++) { // scan at most 128 entries forward
            long pos = dataFile.getFilePointer();
            try {
                int keyLen = dataFile.readInt();
                if (keyLen <= 0 || keyLen > 1024) break;
                byte[] kb = new byte[keyLen];
                dataFile.readFully(kb);
                String k = new String(kb, StandardCharsets.UTF_8);

                int valLen = dataFile.readInt();
                if (valLen < 0 || valLen > 10 * 1024 * 1024) break;
                byte[] vb = new byte[valLen];
                dataFile.readFully(vb);
                long expireAt = dataFile.readLong();
                long createdAt = dataFile.readLong();

                int cmp = k.compareTo(key);
                if (cmp == 0) {
                    if (expireAt > 0 && now >= expireAt) return null;
                    return new String(vb, StandardCharsets.UTF_8);
                }
                if (cmp > 0) break; // passed it
            } catch (IOException e) { break; }
        }
        return null;
    }

    /** Read all entries (for compaction). Skips expired entries. */
    public List<EntryData> readAll() throws IOException {
        List<EntryData> result = new ArrayList<>();
        dataFile.seek(4); // skip magic
        long now = System.currentTimeMillis();
        try {
            while (true) {
                int keyLen = dataFile.readInt();
                byte[] kb = new byte[keyLen];
                dataFile.readFully(kb);
                String key = new String(kb, StandardCharsets.UTF_8);

                int valLen = dataFile.readInt();
                byte[] vb = new byte[valLen];
                dataFile.readFully(vb);
                String value = new String(vb, StandardCharsets.UTF_8);
                long expireAt = dataFile.readLong();
                long createdAt = dataFile.readLong();

                if (expireAt > 0 && now >= expireAt) continue; // skip expired
                result.add(new EntryData(key, value, expireAt, createdAt));
            }
        } catch (IOException e) {
            // EOF reached
        }
        return result;
    }

    /** Read all entries from a GZIP-compressed .sst.gz file (for compaction). */
    public static List<EntryData> readAllGzipped(Path gzPath) throws IOException {
        List<EntryData> result = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(gzPath.toFile()))))) {
            dis.readInt(); // skip magic
            long now = System.currentTimeMillis();
            try {
                while (true) {
                    int keyLen = dis.readInt();
                    byte[] kb = new byte[keyLen];
                    dis.readFully(kb);
                    String key = new String(kb, StandardCharsets.UTF_8);
                    int valLen = dis.readInt();
                    byte[] vb = new byte[valLen];
                    dis.readFully(vb);
                    String value = new String(vb, StandardCharsets.UTF_8);
                    long expireAt = dis.readLong();
                    long createdAt = dis.readLong();
                    if (expireAt > 0 && now >= expireAt) continue;
                    result.add(new EntryData(key, value, expireAt, createdAt));
                }
            } catch (IOException e) { /* EOF */ }
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        dataFile.close();
    }

    public Path path() { return dataPath; }
    public BloomFilter bloomFilter() { return bloom; }

    // ---- Data types ----

    static class IndexEntry {
        final String key;
        final long offset;
        IndexEntry(String k, long o) { key = k; offset = o; }
    }

    public static class EntryData {
        public final String key;
        public final String value;
        public final long expireAt;
        public final long createdAt;
        public EntryData(String k, String v, long ea, long ca) { key = k; value = v; expireAt = ea; createdAt = ca; }
        public Entry toEntry() { return new Entry(key, value, expireAt, createdAt); }
    }
}
