package worker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import worker.messages.Request;
import worker.messages.Response;

import java.nio.charset.StandardCharsets;

/**
 * Utilitário para serialização/deserialização de mensagens usando Gson
 */
public class MessageSerializer {
    private static final Gson gson = new GsonBuilder().create();

    public static byte[] toBytes(Object obj) {
        String json = gson.toJson(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static Request requestFromBytes(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, Request.class);
    }

    public static Response responseFromBytes(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, Response.class);
    }
}

