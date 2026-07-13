package util;

import java.util.regex.Pattern;

public final class Patterns {
    private Patterns() {}

    /** Convert a shell-style glob (`*` = any chars) to a regex matcher. */
    public static boolean matches(String glob, String input) {
        if (glob == null || glob.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*') sb.append(".*");
            else if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-') sb.append(c);
            else sb.append('\\').append(c);   // escape regex metacharacters
        }
        return Pattern.compile(sb.toString()).matcher(input).matches();
    }
}
