package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtil {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonUtil() {}

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String s, Class<T> c) {
        return GSON.fromJson(s, c);
    }
}
