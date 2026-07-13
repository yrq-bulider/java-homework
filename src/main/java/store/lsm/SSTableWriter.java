package store.lsm;

import store.Entry;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a sorted MemTable snapshot as an immutable SSTable.
 *
 * Two output files are produced:
 *   path.sst      — sorted data entries (uncompressed, for random access)
 *   path.sst.idx  — sparse index + bloom filter (small, loaded into memory)
 *
 * Data entry format (binary, big-endian):
 *   keyLen:4 + key:bytes + valueLen:4 + value:bytes + expireAt:8 + createdAt:8
 */
public class SSTableWriter implements AutoCloseable {

    private static final int MAGIC = 0x53535401; // "SST" version 1
    private static final int INDEX_INTERVAL = 64;

    private final DataOutputStream dataOut;
    private final Path dataPath;
    private final Path indexPath;
    private final List<IndexEntry> sparseIndex = new ArrayList<>();
    private final BloomFilter bloom;
    private int entryCount;

    public SSTableWriter(Path basePath, int expectedKeys) throws IOException {
        this.dataPath = basePath;
        String base = basePath.toString().replaceAll("\\.sst$", "");
        this.indexPath = basePath.getFileSystem().getPath(base + ".sst.idx");
        this.bloom = new BloomFilter(Math.max(expectedKeys, 1), 0.01);
        this.dataOut = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(dataPath.toFile())));
        dataOut.writeInt(MAGIC);
    }

    public void writeEntry(String key, Entry entry) throws IOException {
        long offset = dataOut.size();

        if (entryCount % INDEX_INTERVAL == 0) {
            sparseIndex.add(new IndexEntry(key, offset));
        }

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = entry.value().getBytes(StandardCharsets.UTF_8);
        dataOut.writeInt(keyBytes.length);
        dataOut.write(keyBytes);
        dataOut.writeInt(valBytes.length);
        dataOut.write(valBytes);
        dataOut.writeLong(entry.expireAt());
        dataOut.writeLong(entry.createdAt());

        bloom.add(key);
        entryCount++;
    }

    public void writeAll(Iterable<Map.Entry<String, Entry>> entries) throws IOException {
        for (Map.Entry<String, Entry> e : entries) {
            writeEntry(e.getKey(), e.getValue());
        }
    }

    @Override
    public void close() throws IOException {
        dataOut.close();

        // Write companion index file (small, plain text JSON-like format)
        try (DataOutputStream idxOut = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(indexPath.toFile())))) {
            idxOut.writeInt(0x49445831); // "IDX1" magic
            idxOut.writeInt(sparseIndex.size());
            for (IndexEntry ie : sparseIndex) {
                byte[] kb = ie.key.getBytes(StandardCharsets.UTF_8);
                idxOut.writeInt(kb.length);
                idxOut.write(kb);
                idxOut.writeLong(ie.offset);
            }
            // Bloom filter
            byte[] bloomBytes = bloom.toByteArray();
            idxOut.writeInt(bloom.bitSize());
            idxOut.writeInt(bloom.numHashes());
            idxOut.writeInt(bloomBytes.length);
            idxOut.write(bloomBytes);
        }
    }

    public Path dataPath() { return dataPath; }
    public Path indexPath() { return indexPath; }
    public int entryCount() { return entryCount; }

    static class IndexEntry {
        final String key;
        final long offset;
        IndexEntry(String k, long o) { key = k; offset = o; }
    }

    /** GZIP-compress an existing .sst file for archival. */
    public static void compressToGzip(Path sstPath) throws IOException {
        Path gzPath = sstPath.resolveSibling(sstPath.getFileName() + ".gz");
        try (java.io.FileInputStream fis = new java.io.FileInputStream(sstPath.toFile());
             GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzPath.toFile()))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) gos.write(buf, 0, n);
        }
        java.nio.file.Files.delete(sstPath);
    }
}
