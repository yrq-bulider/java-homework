package store;

import util.JsonUtil;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class PersistentStore implements AutoCloseable {

    private static final long ROTATE_THRESHOLD_BYTES = 64L * 1024 * 1024;

    private final Path dataDir;
    private final Path activeFile;
    private BufferedWriter writer;
    private long bytesWritten;

    public PersistentStore(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        this.activeFile = dataDir.resolve("active.jsonl");
        this.writer = openWriter(activeFile);
        this.bytesWritten = Files.size(activeFile);
    }

    private BufferedWriter openWriter(Path p) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(p.toFile(), true), StandardCharsets.UTF_8));
    }

    public synchronized void appendSet(String key, String value, long expireAt) throws IOException {
        appendSet(key, value, Entry.ValueType.STRING, expireAt);
    }

    public synchronized void appendSet(String key, String value, Entry.ValueType valueType, long expireAt) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","SET"); op.put("key", key); op.put("value", value);
        op.put("valueType", valueType.name());
        op.put("expireAt", expireAt);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendDel(String key) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","DEL"); op.put("key", key);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendMset(String[] kvs, long expireAt) throws IOException {
        List<Map<String,String>> items = new ArrayList<>();
        for (int i = 0; i < kvs.length; i += 2) {
            Map<String,String> m = new LinkedHashMap<>();
            m.put("key", kvs[i]); m.put("value", kvs[i+1]);
            items.add(m);
        }
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","MSET"); op.put("items", items);
        op.put("expireAt", expireAt);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendMdel(String[] keys) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","MDEL"); op.put("keys", Arrays.asList(keys));
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendMupd(String[] kvs, long expireAt) throws IOException {
        List<Map<String,String>> items = new ArrayList<>();
        for (int i = 0; i < kvs.length; i += 2) {
            Map<String,String> m = new LinkedHashMap<>();
            m.put("key", kvs[i]); m.put("value", kvs[i+1]);
            items.add(m);
        }
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","MUPD"); op.put("items", items);
        op.put("expireAt", expireAt);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendFlush() throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","FLUSH");
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    private void append(String jsonLine) throws IOException {
        writer.write(jsonLine);
        writer.newLine();
        writer.flush();
        bytesWritten += jsonLine.length() + 1;
        if (bytesWritten >= ROTATE_THRESHOLD_BYTES) {
            rotate();
        }
    }

    /** Renames active.jsonl to rotated-NNN.jsonl, then opens a new active.jsonl. */
    private void rotate() throws IOException {
        writer.close();
        int next = nextRotateIndex();
        Path target = dataDir.resolve(String.format("rotated-%03d.jsonl", next));
        Files.move(activeFile, target);
        Logger.info("Rotated active.jsonl to " + target.getFileName());
        writer = openWriter(activeFile);
        bytesWritten = 0;
    }

    private int nextRotateIndex() {
        int max = -1;
        try {
            try (Stream<Path> s = Files.list(dataDir)) {
                List<Path> files = s.filter(p -> p.getFileName().toString().startsWith("rotated-")).collect(Collectors.toList());
                for (Path p : files) {
                    String n = p.getFileName().toString();
                    int dash = n.indexOf('-');
                    int dot = n.lastIndexOf('.');
                    try {
                        int idx = Integer.parseInt(n.substring(dash + 1, dot));
                        if (idx > max) max = idx;
                    } catch (NumberFormatException ignore) {}
                }
            }
        } catch (IOException e) {
            Logger.warn("nextRotateIndex failed: " + e.getMessage());
        }
        return max + 1;
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) writer.close();
    }

    public Path dataDir() { return dataDir; }
    public Path activeFile() { return activeFile; }

    /** Reads all .jsonl and .jsonl.gz files in dataDir, replays ops into the store.
     *  Files are processed in sorted order: rotated-NNN.jsonl files first (by name), then active.jsonl last. */
    public static void replay(Path dataDir, NormalStore store) throws IOException {
        if (!Files.exists(dataDir)) return;
        List<Path> files;
        try (Stream<Path> s = Files.list(dataDir)) {
            files = s
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".jsonl") || n.endsWith(".jsonl.gz");
                })
                .sorted((a, b) -> {
                    // active.jsonl (and .gz variants) come last; everything else sorted lexicographically
                    String an = a.getFileName().toString();
                    String bn = b.getFileName().toString();
                    boolean aActive = an.startsWith("active.");
                    boolean bActive = bn.startsWith("active.");
                    if (aActive && bActive) return 0;
                    if (aActive) return 1;
                    if (bActive) return -1;
                    return an.compareTo(bn);
                })
                .collect(Collectors.toList());
        }
        store.setTransient(true);
        try {
            for (Path f : files) {
                applyFileToStore(f, store);
            }
        } finally {
            store.setTransient(false);
        }
    }

    private static void applyFileToStore(Path f, NormalStore store) throws IOException {
        InputStream raw = Files.newInputStream(f);
        InputStream in;
        if (f.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(raw);
        } else {
            in = raw;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            long now = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                applyLine(line, store, now);
            }
        }
    }

    private static void applyLine(String json, NormalStore store, long now) {
        try {
            Map<?,?> op = JsonUtil.fromJson(json, Map.class);
            String name = (String) op.get("op");
            if (name == null) return;
            switch (name) {
                case "SET": {
                    String key = (String) op.get("key");
                    String value = (String) op.get("value");
                    long expireAt = ((Number) op.get("expireAt")).longValue();
                    if (expireAt > 0 && now >= expireAt) break;     // skip expired
                    long ttl = (expireAt > 0) ? Math.max(1, (expireAt - now) / 1000) : 0;
                    String vtStr = (String) op.get("valueType");
                    Entry.ValueType vt = Entry.ValueType.STRING;
                    if (vtStr != null) {
                        try { vt = Entry.ValueType.valueOf(vtStr); } catch (IllegalArgumentException ignored) {}
                    }
                    store.setWithType(key, value, vt, ttl);
                    break;
                }
                case "DEL":   store.del((String) op.get("key")); break;
                case "MSET": {
                    @SuppressWarnings("unchecked")
                    List<Map<String,String>> items = (List<Map<String,String>>) op.get("items");
                    long expireAt = ((Number) op.get("expireAt")).longValue();
                    long ttl = (expireAt > 0) ? Math.max(1, (expireAt - now) / 1000) : 0;
                    String[] kvs = new String[items.size() * 2];
                    for (int i = 0; i < items.size(); i++) {
                        Map<String,String> m = items.get(i);
                        kvs[i*2] = m.get("key");
                        kvs[i*2+1] = m.get("value");
                    }
                    store.mset(kvs, ttl);
                    break;
                }
                case "MDEL": {
                    @SuppressWarnings("unchecked")
                    List<String> keys = (List<String>) op.get("keys");
                    store.mdel(keys.toArray(new String[0]));
                    break;
                }
                case "MUPD": {
                    @SuppressWarnings("unchecked")
                    List<Map<String,String>> items = (List<Map<String,String>>) op.get("items");
                    long expireAt = ((Number) op.get("expireAt")).longValue();
                    long ttl = (expireAt > 0) ? Math.max(1, (expireAt - now) / 1000) : 0;
                    String[] kvs = new String[items.size() * 2];
                    for (int i = 0; i < items.size(); i++) {
                        Map<String,String> m = items.get(i);
                        kvs[i*2] = m.get("key");
                        kvs[i*2+1] = m.get("value");
                    }
                    store.mupd(kvs, ttl);
                    break;
                }
                case "FLUSH": store.flush(); break;
                default: Logger.warn("replay: unknown op " + name);
            }
        } catch (Exception e) {
            Logger.warn("replay: bad line skipped: " + e.getMessage());
        }
    }
}
