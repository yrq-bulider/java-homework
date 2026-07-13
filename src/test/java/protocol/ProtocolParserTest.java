package protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolParserTest {
    @Test public void parsesSetSimple() {
        Request r = ProtocolParser.parse("SET foo bar");
        assertEquals("SET", r.verb());
        assertEquals(2, r.argCount());
        assertEquals("foo", r.args().get(0));
        assertEquals("bar", r.args().get(1));
    }

    @Test public void parsesQuotedValueWithSpace() {
        // Quoted form lets the value contain whitespace.
        // Bare-token values do NOT support embedded spaces — users must quote.
        Request r = ProtocolParser.parse("SET name \"张 三\"");
        assertEquals("SET", r.verb());
        assertEquals(2, r.argCount());
        assertEquals("name", r.args().get(0));
        assertEquals("张 三", r.args().get(1));
    }

    @Test public void parsesWithTtl() {
        Request r = ProtocolParser.parse("SET foo bar 60");
        assertEquals(3, r.argCount());
        assertEquals("60", r.args().get(2));   // parser keeps it as string; handler validates as number
    }

    @Test public void parsesFlush() {
        Request r = ProtocolParser.parse("FLUSH");
        assertEquals("FLUSH", r.verb());
        assertEquals(0, r.argCount());
    }

    @Test public void emptyLineThrows() {
        try { ProtocolParser.parse(""); fail(); }
        catch (ProtocolParser.ParseException e) {}
    }

    @Test public void nullThrows() {
        try { ProtocolParser.parse(null); fail(); }
        catch (ProtocolParser.ParseException e) {}
    }

    @Test public void parsesLowercase() {
        Request r = ProtocolParser.parse("set foo bar");
        assertEquals("SET", r.verb());
    }

    @Test public void bareWordsAreSeparateTokens() {
        // Bare tokens are whitespace-separated. To use spaces inside a value,
        // the user MUST quote it.
        Request r = ProtocolParser.parse("SET foo bar 60");
        assertEquals(3, r.argCount());
        assertEquals("foo", r.args().get(0));
        assertEquals("bar", r.args().get(1));
        assertEquals("60", r.args().get(2));
    }
}