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
}
