package handler;

import protocol.Request;
import protocol.Response;
import store.Collection;
import store.CollectionManager;
import store.NormalStore;

import java.util.ArrayList;
import java.util.List;

/**
 * COLLECTION command — exposes the Collection abstraction via the wire protocol.
 *
 * Sub-commands:
 *   COLLECTION LIST                       → list all collection names
 *   COLLECTION KEYS <name>                → list keys in a collection (without prefix)
 *   COLLECTION GET <name> <key>           → get value from a collection
 *   COLLECTION SET <name> <key> <value>   → set value in a collection
 *   COLLECTION DEL <name> <key>           → delete key from a collection
 */
public class CollectionCmdHandler implements CommandHandler {
    @Override public String verb() { return "COLLECTION"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 1)
            return Response.error("wrong number of arguments for 'COLLECTION' — usage: COLLECTION LIST|KEYS|GET|SET|DEL ...");

        String sub = req.args().get(0).toUpperCase();
        CollectionManager cm = new CollectionManager(store);

        try {
            switch (sub) {
                case "LIST": {
                    List<String> cols = cm.listCollections();
                    if (cols.isEmpty()) return Response.multi(new ArrayList<String>());
                    return Response.multi(cols);
                }
                case "KEYS": {
                    if (req.argCount() < 2)
                        return Response.error("usage: COLLECTION KEYS <name>");
                    String name = req.args().get(1);
                    Collection col = cm.collection(name);
                    List<String> keys = col.listKeys();
                    return Response.multi(keys);
                }
                case "GET": {
                    if (req.argCount() < 3)
                        return Response.error("usage: COLLECTION GET <name> <key>");
                    String name = req.args().get(1);
                    String key = req.args().get(2);
                    Collection col = cm.collection(name);
                    String value = col.get(key);
                    return value != null ? Response.value(value) : Response.nil();
                }
                case "SET": {
                    if (req.argCount() < 4)
                        return Response.error("usage: COLLECTION SET <name> <key> <value>");
                    String name = req.args().get(1);
                    String key = req.args().get(2);
                    String value = req.args().get(3);
                    long ttl = req.argCount() >= 5 ? Long.parseLong(req.args().get(4)) : 0;
                    Collection col = cm.collection(name);
                    col.set(key, value, ttl);
                    return Response.ok();
                }
                case "DEL": {
                    if (req.argCount() < 3)
                        return Response.error("usage: COLLECTION DEL <name> <key>");
                    String name = req.args().get(1);
                    String key = req.args().get(2);
                    Collection col = cm.collection(name);
                    boolean removed = col.del(key);
                    return Response.integer(removed ? 1 : 0);
                }
                default:
                    return Response.error(
                            "unknown COLLECTION sub-command: " + sub
                            + " — valid: LIST, KEYS, GET, SET, DEL");
            }
        } catch (NumberFormatException e) {
            return Response.error("value is not an integer or out of range");
        }
    }
}
