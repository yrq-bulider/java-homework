package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

/**
 * RENAME — 重命名 key（可用于在 collection 间转移数据）。
 * 语法: RENAME <oldKey> <newKey>
 */
public class RenameHandler implements CommandHandler {
    @Override public String verb() { return "RENAME"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 2)
            return Response.error("wrong number of arguments for 'RENAME' — usage: RENAME <oldKey> <newKey>");

        String oldKey = req.args().get(0);
        String newKey = req.args().get(1);
        String value = store.get(oldKey);
        if (value == null)
            return Response.error("no such key: " + oldKey);

        store.set(newKey, value, 0);
        store.del(oldKey);
        return Response.ok();
    }
}
