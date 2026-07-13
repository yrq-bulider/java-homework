package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class QuitHandler implements CommandHandler {
    @Override public String verb() { return "QUIT"; }

    @Override public Response handle(Request req, NormalStore store) {
        return Response.ok();
    }
}