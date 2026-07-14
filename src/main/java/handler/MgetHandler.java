package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.ArrayList;
import java.util.List;

/**
 * MGET — 批量查询多个 key 的值。
 * 语法: MGET k1 k2 k3 ...
 * 返回: 多行响应，每行一个 value 或 (nil)
 */
public class MgetHandler implements CommandHandler {
    @Override public String verb() { return "MGET"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 1)
            return Response.error("wrong number of arguments for 'MGET'");

        List<String> values = new ArrayList<>();
        for (String key : req.args()) {
            String v = store.get(key);
            values.add(v != null ? v : "(nil)");
        }
        return Response.multi(values);
    }
}
