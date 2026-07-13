package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class FlushHandler implements CommandHandler {
    @Override public String verb() { return "FLUSH"; }

    @Override public Response handle(Request req, NormalStore store) {
        store.flush();
        return Response.ok();
    }
}