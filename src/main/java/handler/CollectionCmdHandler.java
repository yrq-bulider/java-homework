package handler;

import protocol.Request;
import protocol.Response;
import store.CollectionManager;
import store.NormalStore;

import java.util.ArrayList;
import java.util.List;

/**
 * COLLECTION command — collection-level operations.
 *
 * Sub-commands:
 *   COLLECTION LIST            → list all collection names (declared + with data)
 *   COLLECTION CREATE <name>   → declare a collection (errors if already exists)
 *   COLLECTION DROP <name>     → delete collection declaration + all its keys
 *
 * Use USE <name> for context-scoped GET/SET/DEL/KEYS — those are not duplicated here.
 */
public class CollectionCmdHandler implements CommandHandler {
    @Override public String verb() { return "COLLECTION"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 1)
            return Response.error("wrong number of arguments for 'COLLECTION' — usage: COLLECTION LIST|CREATE|DROP ...");

        String sub = req.args().get(0).toUpperCase();
        CollectionManager cm = new CollectionManager(store);

        try {
            switch (sub) {
                case "LIST": {
                    List<String> cols = cm.listCollections();
                    if (cols.isEmpty()) return Response.multi(new ArrayList<String>());
                    return Response.multi(cols);
                }
                case "CREATE": {
                    if (req.argCount() < 2)
                        return Response.error("usage: COLLECTION CREATE <name>");
                    String name = req.args().get(1);
                    if (name.contains(":"))
                        return Response.error("collection name cannot contain ':'");
                    if (!cm.createCollection(name))
                        return Response.error("collection already exists: " + name);
                    return Response.ok();
                }
                case "DROP": {
                    if (req.argCount() < 2)
                        return Response.error("usage: COLLECTION DROP <name>");
                    String name = req.args().get(1);
                    int removed = cm.dropCollection(name);
                    return Response.integer(removed);
                }
                default:
                    return Response.error(
                            "unknown COLLECTION sub-command: " + sub
                            + " — valid: LIST, CREATE, DROP");
            }
        } catch (NumberFormatException e) {
            return Response.error("value is not an integer or out of range");
        }
    }
}