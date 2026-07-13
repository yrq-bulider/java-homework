package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class GetHandler implements CommandHandler {
    @Override public String verb() { return "GET"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 1) return Response.error("wrong number of arguments for 'GET'");
        String value = store.get(req.args().get(0));
        return value != null ? Response.value(value) : Response.nil();
    }
}
