package br.com.wlabs.hayiz.doc.listener.integration.capmonster;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.integration.capmonster.model.Captcha;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.Data;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public @Data class Capmonster {

    private String clientKey;

    private int defaultTimeout = 120;

    private int pollingInterval = 10;

    private final OkHttpClient client = new OkHttpClient();

    public Capmonster(String clientKey) {
        this.clientKey = clientKey;
    }

    public double balance() throws CaptchaException, IOException {
        HashMap<String, Object> createTask = new HashMap<>();
        createTask.put("clientKey", this.clientKey);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new Gson().toJson(createTask), mediaType);

        Request request = new Request.Builder()
                .url("https://api.capmonster.cloud/getBalance")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        String json = this.execute(request);
        HashMap<String, Object> response = this.fromJSON(json);
        this.validateResponse(response);
        return (double) response.get("balance");
    }

    public void report(Integer taskId, boolean correct) throws Exception {
       System.out.println("taskId: " + taskId + " correct: " + correct);
    }

    public void solve(Captcha captcha) throws CaptchaException, java.io.IOException {
        HashMap<String, Object> createTask = new HashMap<>();
        createTask.put("clientKey", this.clientKey);
        createTask.put("task", captcha.getParams());

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new Gson().toJson(createTask), mediaType);

        Request request = new Request.Builder()
                .url("https://api.capmonster.cloud/createTask")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        String json = this.execute(request);
        HashMap<String, Object> response = this.fromJSON(json);
        this.validateResponse(response);
        captcha.setTaskId((Integer) response.get("taskId"));

        long startedAt = System.currentTimeMillis() / 1000;
        while (true) {
            long now = System.currentTimeMillis() / 1000;
            if (now - startedAt < this.defaultTimeout) {
                try {
                    Thread.sleep(this.pollingInterval * 1000);
                } catch (InterruptedException exception) {
                    //TODO
                }
            } else {
                break;
            }

            try {
                HashMap<String, Object> taskResult = new HashMap<>();
                taskResult.put("clientKey", this.clientKey);
                taskResult.put("taskId", captcha.getTaskId());

                body = RequestBody.create(new Gson().toJson(taskResult), mediaType);
                request = new Request.Builder()
                        .url(" https://api.capmonster.cloud/getTaskResult")
                        .method("POST", body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                json = this.execute(request);
                response = this.fromJSON(json);
                this.validateResponse(response);
                if(Objects.equals(response.get("status").toString(), "ready")) {
                    captcha.setSolution((HashMap<String, Object>) response.get("solution"));
                    return;
                }
            } catch (IOException e) {
                // ignore network errors
            }
        }

        throw new CaptchaException("Timeout " + this.defaultTimeout + " seconds reached");
    }

    private String execute(Request request) throws CaptchaException, java.io.IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new CaptchaException("Unexpected code " + response);
            }

            String body = response.body().string();

            if (body.startsWith("ERROR_")) {
                throw new CaptchaException(body);
            }

            return body;
        }
    }

    private static <T> T fromJSON(String json) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<T> typeRef = new TypeReference<T>() {};
        return mapper.readValue(json, typeRef);
    }

    public void validateResponse(HashMap<String, Object> response) throws CaptchaException {
        if(response.containsKey("errorId")) {
            int errorId = (int) response.get("errorId");
            if(errorId > 0) {
                String errorCode = response.getOrDefault("errorCode", "ERROR_UNKNOWN").toString();
                throw new CaptchaException(errorCode);
            }
        }
    }
}
