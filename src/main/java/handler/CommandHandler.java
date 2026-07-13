package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public interface CommandHandler {
    /** @return the command verb this handler responds to (uppercased). */
    String verb();

    Response handle(Request request, NormalStore store);
}