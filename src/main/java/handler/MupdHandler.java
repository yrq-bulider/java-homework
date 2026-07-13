package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class MupdHandler implements CommandHandler {
    @Override public String verb() { return "MUPD"; }

    @Override public Response handle(Request req, NormalStore store) {
        int n = req.argCount();
        if (n < 2 || n % 2 != 0)
            return Response.error("wrong number of arguments for 'MUPD'");
        String[] kvs = req.args().toArray(new String[0]);
        int applied = store.mupd(kvs, 0);
        return Response.integer(applied);
    }
}
