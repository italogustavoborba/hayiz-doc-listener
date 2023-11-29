package br.com.wlabs.hayiz.doc.listener.session;

import br.com.wlabs.hayiz.doc.listener.properties.ApplicationContextProvider;
import br.com.wlabs.hayiz.doc.listener.session.model.Invoke;
import br.com.wlabs.hayiz.doc.listener.session.model.Session;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.time.Duration;
import java.util.UUID;

public class SessionManagement {

    private final static String url;

    private final static ObjectMapper objectMapper;

    static {
        url = ApplicationContextProvider.getEnvironmentProperty("hayiz.session-management.base-url",
                String.class, "");
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Session invoke(Invoke invoke) throws Exception {
        OkHttpClient httpClient = new OkHttpClient().newBuilder()
                .callTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(invoke), mediaType);
        Request request = new Request.Builder()
                .url(url + "/hayiz-bot-session/v1/session/invoke")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful()) {
                throw new Exception("Session Management Failed");
            }
            return objectMapper.readValue(response.body().byteStream(), Session.class);
        }
    }

    public static void revoke(UUID uuid) throws Exception {
        OkHttpClient httpClient = new OkHttpClient().newBuilder()
                .callTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create("", mediaType);
        Request request = new Request.Builder()
                .url(url + "/hayiz-bot-session/v1/session/revoke/" + uuid)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        httpClient.newCall(request).execute();
    }
}
