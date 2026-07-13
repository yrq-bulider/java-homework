package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class PingHandler implements CommandHandler {
    @Override public String verb() { return "PING"; }

    @Override public Response handle(Request req, NormalStore store) {
        return Response.value("PONG");
    }
}