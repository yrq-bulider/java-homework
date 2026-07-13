package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class DelHandler implements CommandHandler {
    @Override public String verb() { return "DEL"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 1) return Response.error("wrong number of arguments for 'DEL'");
        boolean removed = store.del(req.args().get(0));
        return Response.integer(removed ? 1 : 0);
    }
}