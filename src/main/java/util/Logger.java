package util;

import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

public class Logger {
    private static final java.util.logging.Logger JUL = java.util.logging.Logger.getLogger("easy-db");

    static {
        JUL.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        JUL.addHandler(handler);
        JUL.setLevel(Level.INFO);
    }

    public static void info(String msg)                       { JUL.info(msg); }
    public static void warn(String msg)                       { JUL.warning(msg); }
    public static void error(String msg, Throwable t)         { JUL.log(Level.SEVERE, msg, t); }
    public static void error(String msg)                      { JUL.severe(msg); }
}
