package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class MsetHandler implements CommandHandler {
    @Override public String verb() { return "MSET"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 2 || req.argCount() % 2 != 0)
            return Response.error("wrong number of arguments for 'MSET'");
        String[] kv = req.args().toArray(new String[0]);
        store.mset(kv, 0);
        return Response.ok();
    }
}