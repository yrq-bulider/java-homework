package store;

import org.junit.Test;
import static org.junit.Assert.*;

public class NormalStoreTest {
    @Test public void setAndGet()       { NormalStore s = new NormalStore(); s.set("k", "v", 0); assertEquals("v", s.get("k")); }
    @Test public void getMissing()      { NormalStore s = new NormalStore(); assertNull(s.get("nope")); }
    @Test public void delExisting()     { NormalStore s = new NormalStore(); s.set("k", "v", 0); assertTrue(s.del("k")); }
    @Test public void delMissing()      { NormalStore s = new NormalStore(); assertFalse(s.del("nope")); }
    @Test public void getAfterDel()     { NormalStore s = new NormalStore(); s.set("k", "v", 0); s.del("k"); assertNull(s.get("k")); }
    @Test public void overwriteValue()  { NormalStore s = new NormalStore(); s.set("k", "v1", 0); s.set("k", "v2", 0); assertEquals("v2", s.get("k")); }
    @Test public void sizeReflectsKeys(){ NormalStore s = new NormalStore(); s.set("k1","v",0); s.set("k2","v",0); assertEquals(2, s.size()); }
    @Test public void ttlExpires() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 1);                       // 1 second TTL
        assertEquals("v", s.get("k"));
        Thread.sleep(1100);
        assertNull(s.get("k"));
    }
    @Test public void ttlZeroIsForever() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 0);
        Thread.sleep(200);
        assertEquals("v", s.get("k"));
    }
    @Test public void setNewTtlOverwrites() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 1);
        s.set("k", "v2", 0);                      // overwrite with no TTL
        Thread.sleep(1100);
        assertEquals("v2", s.get("k"));
    }

    @Test public void msetInsertsAll() {
        NormalStore s = new NormalStore();
        int n = s.mset(new String[]{"k1","v1","k2","v2"}, 0);
        assertEquals(2, n);
        assertEquals("v1", s.get("k1"));
        assertEquals("v2", s.get("k2"));
    }

    @Test public void msetOddArgsIsZero() {
        NormalStore s = new NormalStore();
        assertEquals(0, s.mset(new String[]{"k1","v1","k2"}, 0));
    }

    @Test public void mdelDeletesAll() {
        NormalStore s = new NormalStore();
        s.set("k1","v",0); s.set("k2","v",0); s.set("k3","v",0);
        int n = s.mdel(new String[]{"k1","k2","missing"});
        assertEquals(2, n);
        assertNull(s.get("k1"));
        assertNull(s.get("k2"));
        assertNotNull(s.get("k3"));
    }

    @Test public void mupdUpdatesExisting() {
        NormalStore s = new NormalStore();
        s.set("k1","v1",0); s.set("k2","v2",0);
        int n = s.mupd(new String[]{"k1","new1","k2","new2"}, 0);
        assertEquals(2, n);
        assertEquals("new1", s.get("k1"));
        assertEquals("new2", s.get("k2"));
    }

    @Test public void mupdSkipsNonExisting() {
        NormalStore s = new NormalStore();
        s.set("k1","v1",0);
        int n = s.mupd(new String[]{"k1","new1","noExist","x"}, 0);
        assertEquals(1, n);
        assertEquals("new1", s.get("k1"));
        assertNull(s.get("noExist"));
    }

    @Test public void mupdOddArgsIsZero() {
        NormalStore s = new NormalStore();
        assertEquals(0, s.mupd(new String[]{"k1","v1","k2"}, 0));
    }

    @Test public void mupdAllMissingReturnsZero() {
        NormalStore s = new NormalStore();
        int n = s.mupd(new String[]{"no1","v1","no2","v2"}, 0);
        assertEquals(0, n);
    }

    @Test public void flushClearsAll() {
        NormalStore s = new NormalStore();
        s.set("k1","v",0); s.set("k2","v",0);
        s.flush();
        assertEquals(0, s.size());
    }

    @Test public void keysListsAll() {
        NormalStore s = new NormalStore();
        s.set("user:001","v",0); s.set("user:002","v",0); s.set("other","v",0);
        java.util.List<String> all = s.keys("*");
        assertEquals(3, all.size());
    }

    @Test public void keysWithPattern() {
        NormalStore s = new NormalStore();
        s.set("user:001","v",0); s.set("user:002","v",0); s.set("other","v",0);
        java.util.List<String> matched = s.keys("user:*");
        assertEquals(2, matched.size());
    }

    @Test public void existsReturnsBool() {
        NormalStore s = new NormalStore();
        s.set("k","v",0);
        assertTrue(s.exists("k"));
        assertFalse(s.exists("nope"));
    }
}
