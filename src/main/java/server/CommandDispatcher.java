package server;

import handler.CommandHandler;
import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final NormalStore store;

    public CommandDispatcher(NormalStore store) {
        this.store = store;
    }

    public CommandDispatcher register(CommandHandler handler) {
        handlers.put(handler.verb().toUpperCase(), handler);
        return this;
    }

    public Response dispatch(Request req) {
        CommandHandler h = handlers.get(req.verb());
        if (h == null) return Response.error("unknown command '" + req.verb() + "'");
        try {
            return h.handle(req, store);
        } catch (RuntimeException e) {
            return Response.error(e.getMessage() == null ? "internal error" : e.getMessage());
        }
    }

    public boolean isQuit(Request req) { return req.isQuit(); }
}
