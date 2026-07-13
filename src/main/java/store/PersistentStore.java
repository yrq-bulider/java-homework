package store;

import util.JsonUtil;
import util.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
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
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","SET"); op.put("key", key); op.put("value", value);
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
}
