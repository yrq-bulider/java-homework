package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.List;

public class KeysHandler implements CommandHandler {
    @Override public String verb() { return "KEYS"; }

    @Override public Response handle(Request req, NormalStore store) {
        String pattern = req.argCount() >= 1 ? req.args().get(0) : "*";
        List<String> keys = store.keys(pattern);
        return Response.multi(keys);
    }
}
