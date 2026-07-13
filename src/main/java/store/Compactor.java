package store;

import util.JsonUtil;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class Compactor {
    private final Path dataDir;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService compressPool;
    private final long intervalMinutes;

    public Compactor(Path dataDir) {
        this(dataDir, 5, 2);
    }

    public Compactor(Path dataDir, long intervalMinutes, int parallelThreads) {
        this.dataDir = dataDir;
        this.intervalMinutes = intervalMinutes;
        this.compressPool = Executors.newFixedThreadPool(parallelThreads);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() { safeScanAndCompress(); }
        }, 30, intervalMinutes, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
        compressPool.shutdown();
        try { compressPool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /** Public test-friendly entry point. Returns count of files compressed. */
    public int scanAndCompress() throws Exception {
        if (!Files.exists(dataDir)) return 0;
        List<Path> uncompressed;
        try (Stream<Path> s = Files.list(dataDir)) {
            uncompressed = s
                .filter(p -> p.getFileName().toString().startsWith("rotated-"))
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .collect(Collectors.toList());
        }
        if (uncompressed.isEmpty()) return 0;
        List<Future<Integer>> futures = new ArrayList<>();
        for (Path f : uncompressed) {
            futures.add(compressPool.submit(new java.util.concurrent.Callable<Integer>() {
                public Integer call() { return compressFile(f); }
            }));
        }
        int total = 0;
        for (Future<Integer> f : futures) total += f.get();
        return total;
    }

    private void safeScanAndCompress() {
        try { scanAndCompress(); }
        catch (Exception e) { Logger.warn("compactor tick failed: " + e.getMessage()); }
    }

    int compressFile(Path file) {
        try {
            Map<String, String> latest = new LinkedHashMap<>();
            Set<String> tombstoned = new HashSet<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                long now = System.currentTimeMillis();
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    Map<?,?> op = JsonUtil.fromJson(line, Map.class);
                    String name = (String) op.get("op");
                    if (name == null) continue;
                    if ("SET".equals(name)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String,Object>> items = (List<Map<String,Object>>) (List<?>) Arrays.asList(op);
                        for (Map<?,?> m : items) {
                            String k = (String) m.get("key");
                            String v = (String) m.get("value");
                            long exp = m.containsKey("expireAt") && m.get("expireAt") != null
                                ? ((Number) m.get("expireAt")).longValue() : 0L;
                            tombstoned.remove(k);
                            if (exp > 0 && now >= exp) {
                                latest.remove(k);
                                tombstoned.add(k);
                            } else {
                                latest.put(k, v);
                            }
                        }
                    } else if ("MSET".equals(name)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String,Object>> items = (List<Map<String,Object>>) op.get("items");
                        for (Map<?,?> m : items) {
                            String k = (String) m.get("key");
                            String v = (String) m.get("value");
                            long exp = m.containsKey("expireAt") && m.get("expireAt") != null
                                ? ((Number) m.get("expireAt")).longValue() : 0L;
                            tombstoned.remove(k);
                            if (exp > 0 && now >= exp) {
                                latest.remove(k);
                                tombstoned.add(k);
                            } else {
                                latest.put(k, v);
                            }
                        }
                    } else if ("DEL".equals(name)) {
                        String k = (String) op.get("key");
                        latest.remove(k);
                        tombstoned.add(k);
                    } else if ("MDEL".equals(name)) {
                        @SuppressWarnings("unchecked")
                        List<String> keys = (List<String>) op.get("keys");
                        for (String k : keys) {
                            latest.remove(k);
                            tombstoned.add(k);
                        }
                    } else if ("FLUSH".equals(name)) {
                        latest.clear();
                        tombstoned.clear();
                    }
                }
            }

            String fname = file.getFileName().toString();
            String baseName = fname.substring(0, fname.length() - ".jsonl".length());
            Path target = file.resolveSibling(baseName + ".jsonl.gz");
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(target.toFile())), StandardCharsets.UTF_8))) {
                for (Map.Entry<String,String> e : latest.entrySet()) {
                    Map<String,Object> op = new LinkedHashMap<>();
                    op.put("op","SET");
                    op.put("key", e.getKey());
                    op.put("value", e.getValue());
                    op.put("expireAt", 0);
                    op.put("ts", System.currentTimeMillis());
                    w.write(JsonUtil.toJson(op));
                    w.newLine();
                }
            }
            Files.delete(file);
            Logger.info("Compressed " + fname + " to " + target.getFileName() + " (" + latest.size() + " entries)");
            return 1;
        } catch (Exception e) {
            Logger.warn("compressFile " + file + ": " + e.getMessage());
            return 0;
        }
    }
}