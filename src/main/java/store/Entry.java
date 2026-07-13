package store;

public final class Entry {
    private final String key;
    private final String value;
    private final long expireAt;     // 0 = never
    private final long createdAt;

    public Entry(String key, String value, long expireAt, long createdAt) {
        this.key = key;
        this.value = value;
        this.expireAt = expireAt;
        this.createdAt = createdAt;
    }

    public String key()       { return key; }
    public String value()     { return value; }
    public long expireAt()    { return expireAt; }
    public long createdAt()   { return createdAt; }

    /** True if this entry has expired (and expireAt > 0). */
    public boolean isExpired(long nowMs) {
        return expireAt > 0 && nowMs >= expireAt;
    }

    public Entry withValue(String newValue, long newExpireAt) {
        return new Entry(key, newValue, newExpireAt, createdAt);
    }
}