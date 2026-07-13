package protocol;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ResponseTest {
    @Test public void ok()           { assertEquals("OK",            Response.ok().toWire()); }
    @Test public void nil()           { assertEquals("(nil)",         Response.nil().toWire()); }
    @Test public void integer()       { assertEquals("(integer) 3",   Response.integer(3).toWire()); }
    @Test public void value()         { assertEquals("\"foo\"",       Response.value("foo").toWire()); }
    @Test public void valueWithQuote(){ assertEquals("\"a\\\"b\"",    Response.value("a\"b").toWire()); }
    @Test public void valueWithNewlineIsError() {
        assertTrue(Response.value("a\nb").toWire().startsWith("(error)"));
    }
    @Test public void error()         { assertEquals("(error) ERR boom", Response.error("boom").toWire()); }

    @Test public void multiToWire() {
        Response.Multi m = new Response.Multi(Arrays.asList("user:001", "user:002"));
        assertEquals("\"user:001\"\n\"user:002\"\n*END", m.toWire());
    }
}
