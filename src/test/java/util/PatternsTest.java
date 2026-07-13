package util;

import org.junit.Test;
import static org.junit.Assert.*;

public class PatternsTest {
    @Test public void exactMatch()      { assertTrue(Patterns.matches("foo", "foo")); }
    @Test public void wildcardAll()     { assertTrue(Patterns.matches("user:*", "user:001")); }
    @Test public void wildcardMiddle()  { assertTrue(Patterns.matches("u*r:001", "user:001")); }
    @Test public void wildcardSuffix()  { assertTrue(Patterns.matches("user:00*", "user:001")); }
    @Test public void noMatch()         { assertFalse(Patterns.matches("foo", "bar")); }
    @Test public void emptyPattern()    { assertFalse(Patterns.matches("", "foo")); }
    @Test public void escapeRegexMeta() { assertTrue(Patterns.matches("a.b", "a.b")); }   // . is literal
}
