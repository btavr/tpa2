package worker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import worker.messages.SpreadMessages;

import java.nio.charset.StandardCharsets;

/**
 * Utilitário para serialização/deserialização de mensagens usando Gson TODO Alterar javadoc
 */
public class MessageSpreadSerializer {
    private static final Gson gson = new GsonBuilder().create();

    public static byte[] toBytes(Object obj) {
        String json = gson.toJson(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static SpreadMessages requestStatisticsFromBytes(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, SpreadMessages.class);
    }
}
