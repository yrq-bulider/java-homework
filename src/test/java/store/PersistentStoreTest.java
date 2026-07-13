package store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import util.JsonUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
}