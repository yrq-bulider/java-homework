package protocol;

import java.util.List;

public final class Response {
    public enum Kind { OK, NIL, INTEGER, VALUE, ERROR, MULTI }

    private final Kind kind;
    private final Object payload;

    private Response(Kind kind, Object payload) {
        this.kind = kind;
        this.payload = payload;
    }

    public static Response ok()                  { return new Response(Kind.OK, null); }
    public static Response nil()                 { return new Response(Kind.NIL, null); }
    public static Response integer(int n)        { return new Response(Kind.INTEGER, n); }
    public static Response value(String s)       { return new Response(Kind.VALUE, s); }
    public static Response error(String msg)     { return new Response(Kind.ERROR, "(error) ERR " + msg); }
    public static Response multi(List<String> lines) { return new Response(Kind.MULTI, lines); }

    public Kind kind() { return kind; }

    public String toWire() {
        switch (kind) {
            case OK:      return "OK";
            case NIL:     return "(nil)";
            case INTEGER: return "(integer) " + payload;
            case VALUE:   return quoteValue((String) payload);
            case ERROR:   return (String) payload;
            case MULTI:   return multiToWire((List<String>) payload);
            default:      return "(error) ERR unknown response kind";
        }
    }

    @SuppressWarnings("unchecked")
    private static String multiToWire(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(quoteValue(line)).append('\n');
        sb.append("*END");
        return sb.toString();
    }

    private static String quoteValue(String s) {
        if (s == null || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "(error) ERR value contains newline";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /** Multi-line response for KEYS etc. */
    public static final class Multi {
        public final List<String> lines;
        public Multi(List<String> lines) { this.lines = lines; }

        public String toWire() {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) sb.append(quoteValue(line)).append('\n');
            sb.append("*END");
            return sb.toString();
        }
    }
}
