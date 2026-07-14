package server;

import protocol.ProtocolParser;
import protocol.Request;
import protocol.Response;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RequestHandler implements Runnable {
    private final Socket client;
    private final CommandDispatcher dispatcher;
    private String currentCollection = "";

    public RequestHandler(Socket client, CommandDispatcher dispatcher) {
        this.client = client;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        String peer = client.getRemoteSocketAddress().toString();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;
                Request req;
                try { req = ProtocolParser.parse(line); }
                catch (ProtocolParser.ParseException pe) {
                    out.write(Response.error(pe.getMessage()).toWire()); out.newLine(); out.flush();
                    continue;
                }
                // Handle USE command to switch collection context
                if ("USE".equals(req.verb())) {
                    String name = req.args().isEmpty() ? "" : req.args().get(0);
                    if (name.equals("*")) name = "";
                    currentCollection = name;
                    String msg = name.isEmpty() ? "OK (default)" : "OK (using " + name + ")";
                    out.write("\"" + msg + "\""); out.newLine(); out.flush();
                    continue;
                }
                // Apply collection prefix to key-based commands
                if (!currentCollection.isEmpty()) {
                    req = applyPrefix(req);
                }
                if (dispatcher.isQuit(req)) {
                    out.write(Response.ok().toWire()); out.newLine(); out.flush();
                    break;
                }
                Response res = dispatcher.dispatch(req);
                out.write(res.toWire()); out.newLine(); out.flush();
            }
        } catch (IOException e) {
            Logger.info("connection closed " + peer + " (" + e.getMessage() + ")");
        } catch (Exception e) {
            Logger.error("handler error " + peer, e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    /** Prepend collection prefix to key arguments for key-based commands. */
    private Request applyPrefix(Request req) {
        String verb = req.verb();
        java.util.List<String> args = new java.util.ArrayList<>(req.args());

        switch (verb) {
            case "GET": case "DEL": case "EXISTS":
                if (!args.isEmpty() && !args.get(0).startsWith(currentCollection + ":"))
                    args.set(0, currentCollection + ":" + args.get(0));
                break;
            case "SET":
                if (args.size() >= 1 && !args.get(0).startsWith(currentCollection + ":"))
                    args.set(0, currentCollection + ":" + args.get(0));
                break;
            case "MSET": case "MUPD":
                for (int i = 0; i < args.size(); i += 2)
                    if (!args.get(i).startsWith(currentCollection + ":"))
                        args.set(i, currentCollection + ":" + args.get(i));
                break;
            case "MDEL": case "MGET":
                for (int i = 0; i < args.size(); i++)
                    if (!args.get(i).startsWith(currentCollection + ":"))
                        args.set(i, currentCollection + ":" + args.get(i));
                break;
            case "KEYS":
                if (args.isEmpty()) args.add(currentCollection + ":*");
                else if (!args.get(0).startsWith(currentCollection + ":"))
                    args.set(0, currentCollection + ":" + args.get(0));
                break;
            default:
                break;
        }
        return new protocol.Request(verb, args);
    }
}
