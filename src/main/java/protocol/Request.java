package protocol;

import java.util.List;

public final class Request {
    private final String verb;            // uppercased
    private final List<String> args;      // remaining tokens

    public Request(String verb, List<String> args) {
        this.verb = verb.toUpperCase();
        this.args = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(args));
    }

    public String verb()  { return verb; }
    public List<String> args() { return args; }

    public boolean isQuit() { return "QUIT".equals(verb); }

    public int argCount() { return args.size(); }

    /** Returns args[0] or empty string. */
    public String key() {
        return args.isEmpty() ? "" : args.get(0);
    }

    /** Returns args[idx] or null (used by SET, MSET value extraction). */
    public String valueOrNull(int idx) {
        return idx < args.size() ? args.get(idx) : null;
    }
}