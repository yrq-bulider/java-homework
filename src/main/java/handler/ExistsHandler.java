package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class ExistsHandler implements CommandHandler {
    @Override public String verb() { return "EXISTS"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 1) return Response.error("wrong number of arguments for 'EXISTS'");
        boolean exists = store.exists(req.args().get(0));
        return Response.integer(exists ? 1 : 0);
    }
}
