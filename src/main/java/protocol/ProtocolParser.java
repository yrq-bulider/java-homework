package protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses one line of the wire protocol into a {@link Request}.
 *
 * <p>Grammar (whitespace-separated tokens):
 * <pre>
 *   line     := verb (WS arg)*
 *   verb     := bare-token           // uppercased by Request
 *   arg      := bare-token | quoted-string
 *   bare-token := non-whitespace, non-'"' chars
 *   quoted-string := '"' char* '"'   // '\' escapes the next char (including '"' and '\')
 * </pre>
 *
 * <p>Semantic notes:
 * <ul>
 *   <li>Bare tokens are separated by any run of spaces. To include a space
 *       inside a value, the user MUST quote it (e.g. {@code SET name "张 三"}).</li>
 *   <li>The verb is uppercased by the {@link Request} constructor, so callers
 *       can pass {@code "set foo bar"} and get {@code Request.verb() == "SET"}.</li>
 *   <li>Numeric TTL is kept as a string; the command handler validates and
 *       parses it (so the parser does not need to know which commands accept
 *       a TTL).</li>
 * </ul>
 */
public final class ProtocolParser {

    public static class ParseException extends RuntimeException {
        public ParseException(String m) { super(m); }
    }

    private ProtocolParser() {}

    /**
     * Parses one line of the wire protocol.
     *
     * @param line a single line (no trailing newline expected)
     * @return a Request whose verb is the first token (uppercased) and whose
     *         args list contains the remaining tokens in order
     * @throws ParseException if the line is null, empty, or contains an
     *         unterminated quoted string
     */
    public static Request parse(String line) {
        if (line == null || line.isEmpty()) {
            throw new ParseException("empty line");
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) throw new ParseException("empty line");

        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < trimmed.length()) {
            // skip whitespace
            while (i < trimmed.length() && trimmed.charAt(i) == ' ') i++;
            if (i >= trimmed.length()) break;

            if (trimmed.charAt(i) == '"') {
                // quoted string
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < trimmed.length() && trimmed.charAt(i) != '"') {
                    char c = trimmed.charAt(i);
                    if (c == '\\' && i + 1 < trimmed.length()) {
                        char next = trimmed.charAt(i + 1);
                        sb.append(next);
                        i += 2;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                if (i >= trimmed.length()) throw new ParseException("unterminated quoted string");
                i++; // skip closing quote
                parts.add(sb.toString());
            } else {
                // bare token: read until next whitespace
                int start = i;
                while (i < trimmed.length() && trimmed.charAt(i) != ' ') i++;
                parts.add(trimmed.substring(start, i));
            }
        }
        if (parts.isEmpty()) throw new ParseException("no verb");

        String verb = parts.get(0);
        List<String> args = parts.subList(1, parts.size());
        return new Request(verb, args);
    }
}