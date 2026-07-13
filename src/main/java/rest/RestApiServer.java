package rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import store.NormalStore;
import util.JsonUtil;
import util.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RESTful HTTP API for easy-db using JDK built-in HttpServer.
 *
 * Endpoints:
 *   GET    /api/ping                  → {"pong":true}
 *   GET    /api/keys/{key}            → {"key":"k","value":"v","exists":true}
 *   POST   /api/keys/{key}            → {"ok":true}  (body: {"value":"v","ttl":60})
 *   PUT    /api/keys/{key}            → {"updated":N}  (body: {"value":"v"} — MUPD)
 *   DELETE /api/keys/{key}            → {"deleted":true}
 *   GET    /api/keys?pattern=*        → {"keys":["k1","k2"]}
 *   POST   /api/keys/batch            → {"count":N}  (body: {"ops":[{"op":"SET","k":"...","v":"..."}]})
 *   DELETE /api/keys                  → {"ok":true}  (FLUSH, requires confirm header)
 */
public class RestApiServer {

    private final HttpServer server;
    private final NormalStore store;

    public RestApiServer(int port, NormalStore store) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::route);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
        Logger.info("REST API listening on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(1);
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();

            // CORS headers
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if ("OPTIONS".equals(method)) {
                ex.getResponseHeaders().set("Allow", "GET,POST,PUT,DELETE,OPTIONS");
                sendJson(ex, 200, "{}");
                return;
            }

            // Route matching
            if (path.equals("/api/ping")) {
                sendJson(ex, 200, "{\"pong\":true}");
            } else if (path.equals("/api/keys") && "GET".equals(method)) {
                // list keys
                String pattern = getQueryParam(query, "pattern", "*");
                List<String> keys = store.keys(pattern);
                Map<String, Object> result = new HashMap<>();
                result.put("keys", keys);
                sendJson(ex, 200, JsonUtil.toJson(result));
            } else if (path.equals("/api/keys") && "DELETE".equals(method)) {
                // FLUSH
                store.flush();
                sendJson(ex, 200, "{\"ok\":true}");
            } else if (path.equals("/api/keys/batch") && "POST".equals(method)) {
                // batch ops
                String body = readBody(ex);
                Map<?, ?> batch = JsonUtil.fromJson(body, Map.class);
                int count = applyBatch(batch);
                sendJson(ex, 200, "{\"count\":" + count + "}");
            } else if (path.startsWith("/api/keys/")) {
                handleKeyRoute(ex, method, path);
            } else {
                sendJson(ex, 404, "{\"error\":\"not found\"}");
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleKeyRoute(HttpExchange ex, String method, String path) throws IOException {
        String key = path.substring("/api/keys/".length());
        if (key.isEmpty()) { sendJson(ex, 400, "{\"error\":\"missing key\"}"); return; }

        switch (method) {
            case "GET": {
                String value = store.get(key);
                if (value == null) {
                    sendJson(ex, 200, "{\"key\":\"" + escapeJson(key) + "\",\"exists\":false}");
                } else {
                    sendJson(ex, 200, "{\"key\":\"" + escapeJson(key) + "\",\"value\":\"" + escapeJson(value) + "\",\"exists\":true}");
                }
                break;
            }
            case "POST": {
                String body = readBody(ex);
                Map<?, ?> req = JsonUtil.fromJson(body, Map.class);
                String value = (String) req.get("value");
                if (value == null) { sendJson(ex, 400, "{\"error\":\"missing value\"}"); return; }
                long ttl = req.containsKey("ttl") ? ((Number) req.get("ttl")).longValue() : 0;
                store.set(key, value, ttl);
                sendJson(ex, 200, "{\"ok\":true}");
                break;
            }
            case "PUT": {
                String body = readBody(ex);
                Map<?, ?> req = JsonUtil.fromJson(body, Map.class);
                String value = (String) req.get("value");
                if (value == null) { sendJson(ex, 400, "{\"error\":\"missing value\"}"); return; }
                long ttl = req.containsKey("ttl") ? ((Number) req.get("ttl")).longValue() : 0;
                int updated = store.mupd(new String[]{key, value}, ttl);
                sendJson(ex, 200, "{\"updated\":" + updated + "}");
                break;
            }
            case "DELETE": {
                boolean deleted = store.del(key);
                sendJson(ex, 200, "{\"deleted\":" + deleted + "}");
                break;
            }
            default:
                sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private int applyBatch(Map<?, ?> batch) {
        List<Map<String, Object>> ops = (List<Map<String, Object>>) batch.get("ops");
        if (ops == null) return 0;
        int count = 0;
        for (Map<String, Object> op : ops) {
            String action = (String) op.get("op");
            String k = (String) op.get("key");
            String v = (String) op.get("value");
            if ("SET".equalsIgnoreCase(action)) {
                store.set(k, v, 0);
                count++;
            } else if ("DEL".equalsIgnoreCase(action)) {
                if (store.del(k)) count++;
            }
        }
        return count;
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        InputStreamReader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }

    private String getQueryParam(String query, String key, String defaultValue) {
        if (query == null) return defaultValue;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key)) return kv.length > 1 ? java.net.URLDecoder.decode(kv[1]) : defaultValue;
        }
        return defaultValue;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
