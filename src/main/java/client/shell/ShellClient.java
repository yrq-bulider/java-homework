package client.shell;

import client.SocketClient;
import util.Logger;

import java.util.ArrayList;
import java.util.List;

public class ShellClient {

    private static String host = "127.0.0.1";
    private static int port = 9090;
    private static boolean silent = false;

    public static void main(String[] args) {
        String envHost = System.getenv("EASY_DB_HOST");
        if (envHost != null) host = envHost;
        String envPort = System.getenv("EASY_DB_PORT");
        if (envPort != null) port = Integer.parseInt(envPort);

        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-s") || arg.equals("--silent")) silent = true;
            else if (arg.equals("-h") || arg.equals("--help")) { printHelp(); return; }
            else positional.add(arg);
        }

        if (positional.isEmpty()) { printHelp(); return; }

        String cmd = positional.get(0).toUpperCase();
        String params = String.join(" ", positional.subList(1, positional.size()));
        String wireCmd = cmd + (params.isEmpty() ? "" : " " + params);

        try (SocketClient client = new SocketClient(host, port)) {
            client.connect();
            String paramsFirst = positional.size() >= 2 ? positional.get(1).toUpperCase() : "";
            boolean multiLine = "KEYS".equals(cmd) || "MGET".equals(cmd)
                    || ("COLLECTION".equals(cmd) && ("LIST".equals(paramsFirst) || "KEYS".equals(paramsFirst)))
                    || ("CLUSTER".equals(cmd) && "INFO".equals(paramsFirst));
            String result = multiLine ? client.sendCommandMulti(wireCmd) : client.sendCommand(wireCmd);
            if (result == null) return;
            if (!silent) {
                System.out.println(result);
            } else if (!result.startsWith("(error)")) {
                System.out.println(stripValueQuotes(result));
            }
            if (result.startsWith("(error)")) {
                if (!silent) System.err.println(result);
                System.exit(1);
            }
        } catch (Exception e) {
            if (!silent) System.err.println("(error) " + e.getMessage());
            System.exit(1);
        }
    }

    private static String stripValueQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static void printHelp() {
        System.out.println("easy-db Shell Tool (Java)");
        System.out.println("Usage: easy-db [-s|--silent] [-h|--help] <command> [args...]");
        System.out.println();
        System.out.println("Environment variables:");
        System.out.println("  EASY_DB_HOST   Server host (default: 127.0.0.1)");
        System.out.println("  EASY_DB_PORT   Server port (default: 8080)");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  set <key> <value> [ttlSeconds]   Store a key-value pair");
        System.out.println("  get <key>                        Get a key's value");
        System.out.println("  del <key>                        Delete a key");
        System.out.println("  exists <key>                     Check if a key exists");
        System.out.println("  keys [pattern]                   List keys matching pattern");
        System.out.println("  mset <k1> <v1> <k2> <v2> ...     Multi-set");
        System.out.println("  mdel <k1> <k2> ...               Multi-del");
        System.out.println("  mupd <k1> <v1> <k2> <v2> ...     Multi-update (only existing keys)");
        System.out.println("  flush                            Clear all data");
        System.out.println("  ping                             Health check");
    }
}
