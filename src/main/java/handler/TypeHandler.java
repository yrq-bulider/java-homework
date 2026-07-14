package handler;

import protocol.Request;
import protocol.Response;
import store.Entry;
import store.NormalStore;

/**
 * TYPE — 查询 key 的值类型。
 * 语法: TYPE <key>
 * 返回: "string" / "list" / "map" / "none"
 */
public class TypeHandler implements CommandHandler {
    @Override public String verb() { return "TYPE"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 1)
            return Response.error("wrong number of arguments for 'TYPE'");
        // 通过 try-get 判断类型：需要直接读 Entry 而非 get()
        // get() 只返回 String，所以用 exists + 写入时记录的类型
        String key = req.args().get(0);
        String value = store.get(key);
        if (value == null) return Response.value("none");
        Entry.ValueType vt = NormalStore.detectValueType(value);
        return Response.value(vt.name().toLowerCase());
    }
}
