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
}
