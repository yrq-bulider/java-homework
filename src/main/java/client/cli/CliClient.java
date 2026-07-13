package client.cli;

import client.SocketClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive REPL client for easy-db.
 * Usage: java -cp target/easy-db-server.jar client.cli.CliClient [host] [port]
 */
public class CliClient {

    private static String host = "127.0.0.1";
    private static int port = 9090;
    private final SocketClient client;
    private final List<String> history = new ArrayList<>();
    private int historyCursor = -1;

    public CliClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.client = new SocketClient(host, port);
    }

    public void start() {
        System.out.println("easy-db CLI — interactive mode");
        System.out.println("Connected to " + host + ":" + port);
        System.out.println("Type 'help' for available commands, 'exit' to quit.");
        System.out.println();

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            client.connect();

            while (true) {
                System.out.print("easy-db> ");
                String line = console.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Meta commands
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    System.out.println("Bye.");
                    break;
                }
                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }
                if (line.equalsIgnoreCase("history")) {
                    for (int i = 0; i < history.size(); i++)
                        System.out.println("  " + i + ": " + history.get(i));
                    continue;
                }

                history.add(line);
                historyCursor = history.size();

                // Send command
                try {
                    String cmd = line.trim().toUpperCase();
                    String result;
                    if (cmd.startsWith("KEYS")) {
                        result = client.sendCommandMulti(line);
                    } else {
                        result = client.sendCommand(line);
                    }
                    if (result != null) System.out.println(result);
                } catch (Exception e) {
                    System.err.println("(error) " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("(error) " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  SET <key> <value> [ttl]     Set a key-value pair");
        System.out.println("  GET <key>                    Get a key's value");
        System.out.println("  DEL <key>                    Delete a key");
        System.out.println("  EXISTS <key>                 Check key existence");
        System.out.println("  KEYS [pattern]               List matching keys");
        System.out.println("  MSET <k1> <v1> <k2> <v2>...  Multi-set");
        System.out.println("  MDEL <k1> <k2>...            Multi-delete");
        System.out.println("  MUPD <k1> <v1> <k2> <v2>...  Multi-update");
        System.out.println("  FLUSH                        Clear all data");
        System.out.println("  PING                         Health check");
        System.out.println("  CLUSTER INFO|ROLE|LEADER     Cluster status");
        System.out.println();
        System.out.println("Meta commands:");
        System.out.println("  help     Show this help");
        System.out.println("  history  Show command history");
        System.out.println("  exit     Quit the CLI");
    }

    public static void main(String[] args) {
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        new CliClient(host, port).start();
    }
}
