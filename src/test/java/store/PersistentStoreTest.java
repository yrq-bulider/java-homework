package store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import util.JsonUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PersistentStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void appendCreatesFile() throws Exception {
        File dir = tmp.newFolder("data");
        PersistentStore ps = new PersistentStore(dir.toPath());
        ps.appendSet("k1", "v1", 0L);
        ps.appendSet("k2", "v2", 60_000L);
        ps.close();

        File active = new File(dir, "active.jsonl");
        assertTrue(active.exists());
        String content = new String(Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8);
        assertEquals(2, content.split("\n").length);

        // Inspect first line
        String first = content.split("\n")[0];
        Map<?,?> obj = JsonUtil.fromJson(first, Map.class);
        assertEquals("SET", obj.get("op"));
        assertEquals("k1",  obj.get("key"));
        assertEquals("v1",  obj.get("value"));
        assertEquals(0, ((Number)obj.get("expireAt")).longValue());
    }

    @Test public void appendAllOpTypes() throws Exception {
        File dir = tmp.newFolder("data");
        PersistentStore ps = new PersistentStore(dir.toPath());
        ps.appendSet("k","v",0);
        ps.appendDel("k");
        ps.appendMset(new String[]{"a","1","b","2"}, 0);
        ps.appendMdel(new String[]{"a"});
        ps.appendFlush();
        ps.close();

        File active = new File(dir, "active.jsonl");
        String[] lines = new String(Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8).split("\n");
        assertEquals(5, lines.length);
        assertEquals("SET",    JsonUtil.fromJson(lines[0], Map.class).get("op"));
        assertEquals("DEL",    JsonUtil.fromJson(lines[1], Map.class).get("op"));
        assertEquals("MSET",   JsonUtil.fromJson(lines[2], Map.class).get("op"));
        assertEquals("MDEL",   JsonUtil.fromJson(lines[3], Map.class).get("op"));
        assertEquals("FLUSH",  JsonUtil.fromJson(lines[4], Map.class).get("op"));
    }

    @Test public void recoveryReplaysAllFiles() throws Exception {
        File dir = tmp.newFolder("data2");
        // Write 3 ops, close
        try (PersistentStore ps = new PersistentStore(dir.toPath())) {
            ps.appendSet("k1","v1",0);
            ps.appendSet("k2","v2",0);
            ps.appendDel("k1");
        }
        // Recovery: should produce k2 only
        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("k1"));
        assertEquals("v2", store.get("k2"));
    }

    @Test public void recoveryAcrossRotatedFiles() throws Exception {
        File dir = tmp.newFolder("data3");
        try (PersistentStore ps = new PersistentStore(dir.toPath())) {
            ps.appendSet("a","1",0);
            ps.appendSet("b","2",0);
            ps.appendSet("c","3",0);
        }
        // Manually pre-create a rotated-001.jsonl with 2 ops, then active.jsonl with 3 ops
        Path rotated = dir.toPath().resolve("rotated-001.jsonl");
        Files.write(rotated, java.util.Arrays.asList(
            "{\"op\":\"SET\",\"key\":\"k1\",\"value\":\"v1\",\"expireAt\":0,\"ts\":1}",
            "{\"op\":\"SET\",\"key\":\"k2\",\"value\":\"v2\",\"expireAt\":0,\"ts\":2}"
        ), StandardCharsets.UTF_8);
        Path active = dir.toPath().resolve("active.jsonl");
        Files.write(active, java.util.Arrays.asList(
            "{\"op\":\"DEL\",\"key\":\"k1\",\"ts\":3}",
            "{\"op\":\"FLUSH\",\"ts\":4}",
            "{\"op\":\"SET\",\"key\":\"k3\",\"value\":\"v3\",\"expireAt\":0,\"ts\":5}"
        ), StandardCharsets.UTF_8);

        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("k1"));
        assertNull(store.get("k2"));      // FLUSH wiped
        assertEquals("v3", store.get("k3"));
    }

    @Test public void recoveryIgnoresExpiredTtl() throws Exception {
        File dir = tmp.newFolder("data4");
        Path active = dir.toPath().resolve("active.jsonl");
        long past = System.currentTimeMillis() - 1000;
        Files.write(active, java.util.Arrays.asList(
            "{\"op\":\"SET\",\"key\":\"old\",\"value\":\"x\",\"expireAt\":" + past + ",\"ts\":1}",
            "{\"op\":\"SET\",\"key\":\"kept\",\"value\":\"y\",\"expireAt\":0,\"ts\":2}"
        ), StandardCharsets.UTF_8);

        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("old"));
        assertEquals("y", store.get("kept"));
    }
}