package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class SetHandler implements CommandHandler {
    @Override public String verb() { return "SET"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 2) return Response.error("wrong number of arguments for 'SET'");
        String key = req.args().get(0);
        String value = req.args().get(1);
        long ttl = 0;
        if (req.argCount() >= 3) {
            try { ttl = Long.parseLong(req.args().get(2)); }
            catch (NumberFormatException e) { return Response.error("value is not an integer or out of range"); }
            if (ttl < 0) return Response.error("value is not an integer or out of range");
        }
        store.set(key, value, ttl);
        return Response.ok();
    }
}