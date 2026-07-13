package store.lsm;

import java.util.BitSet;

/**
 * Simple Bloom Filter for fast key-existence checks.
 * Uses double-hashing technique to derive k hash functions from two base hashes.
 */
public class BloomFilter {
    private final BitSet bits;
    private final int numHashes;
    private final int size;

    /** @param expectedKeys number of keys expected
     *  @param falsePositiveRate target false-positive rate (e.g. 0.01 = 1%) */
    public BloomFilter(int expectedKeys, double falsePositiveRate) {
        this.size = optimalBits(expectedKeys, falsePositiveRate);
        this.numHashes = optimalHashes(expectedKeys, size);
        this.bits = new BitSet(size);
    }

    /** Constructor for deserialization. */
    public BloomFilter(int size, int numHashes, byte[] data) {
        this.size = size;
        this.numHashes = numHashes;
        this.bits = BitSet.valueOf(data);
    }

    public void add(String key) {
        int h1 = mixHash(key.hashCode());
        int h2 = mixHash(key.hashCode() * 31 + 17);
        for (int i = 0; i < numHashes; i++) {
            int combined = (h1 + i * h2) & Integer.MAX_VALUE;
            bits.set(combined % size);
        }
    }

    public boolean mightContain(String key) {
        int h1 = mixHash(key.hashCode());
        int h2 = mixHash(key.hashCode() * 31 + 17);
        for (int i = 0; i < numHashes; i++) {
            int combined = (h1 + i * h2) & Integer.MAX_VALUE;
            if (!bits.get(combined % size)) return false;
        }
        return true;
    }

    public int bitSize() { return size; }
    public int numHashes() { return numHashes; }
    public byte[] toByteArray() { return bits.toByteArray(); }

    private static int mixHash(int h) {
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    static int optimalBits(int n, double p) {
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    static int optimalHashes(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
