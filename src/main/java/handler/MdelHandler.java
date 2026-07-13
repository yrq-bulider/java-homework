package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class MdelHandler implements CommandHandler {
    @Override public String verb() { return "MDEL"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 1) return Response.error("wrong number of arguments for 'MDEL'");
        String[] keys = req.args().toArray(new String[0]);
        int n = store.mdel(keys);
        return Response.integer(n);
    }
}