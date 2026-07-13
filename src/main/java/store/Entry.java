package store;

import util.JsonUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Entry {
    public enum ValueType { STRING, LIST, MAP }

    private final String key;
    private final String value;       // JSON-encoded for LIST/MAP types
    private final ValueType valueType;
    private final long expireAt;      // 0 = never
    private final long createdAt;

    /** Legacy constructor for plain string values. */
    public Entry(String key, String value, long expireAt, long createdAt) {
        this(key, value, ValueType.STRING, expireAt, createdAt);
    }

    public Entry(String key, String value, ValueType valueType, long expireAt, long createdAt) {
        this.key = key;
        this.value = value;
        this.valueType = valueType;
        this.expireAt = expireAt;
        this.createdAt = createdAt;
    }

    /** Factory: create entry with a List value (stored as JSON array). */
    public static Entry listEntry(String key, List<String> listValue, long expireAt, long createdAt) {
        return new Entry(key, JsonUtil.toJson(listValue), ValueType.LIST, expireAt, createdAt);
    }

    /** Factory: create entry with a Map value (stored as JSON object). */
    public static Entry mapEntry(String key, Map<String, String> mapValue, long expireAt, long createdAt) {
        return new Entry(key, JsonUtil.toJson(mapValue), ValueType.MAP, expireAt, createdAt);
    }

    public String key()       { return key; }
    public String value()     { return value; }
    public ValueType valueType() { return valueType; }
    public long expireAt()    { return expireAt; }
    public long createdAt()   { return createdAt; }

    /** True if this entry has expired (and expireAt > 0). */
    public boolean isExpired(long nowMs) {
        return expireAt > 0 && nowMs >= expireAt;
    }

    /** Parse value as List (only valid when valueType == LIST). */
    @SuppressWarnings("unchecked")
    public List<String> listValue() {
        if (valueType != ValueType.LIST) throw new IllegalStateException("not a LIST value");
        return JsonUtil.fromJson(value, List.class);
    }

    /** Parse value as Map (only valid when valueType == MAP). */
    @SuppressWarnings("unchecked")
    public Map<String, String> mapValue() {
        if (valueType != ValueType.MAP) throw new IllegalStateException("not a MAP value");
        return JsonUtil.fromJson(value, Map.class);
    }

    public Entry withValue(String newValue, long newExpireAt) {
        return new Entry(key, newValue, valueType, newExpireAt, createdAt);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entry)) return false;
        Entry entry = (Entry) o;
        return Objects.equals(key, entry.key);
    }

    @Override public int hashCode() {
        return Objects.hash(key);
    }
}
