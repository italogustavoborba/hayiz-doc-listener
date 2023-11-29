package br.com.wlabs.hayiz.doc.listener.integration.capmonster.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

public @Data
abstract class Captcha {

    protected Integer taskId;
    protected Map<String, Object> solution;

    protected Map<String, Object> params;

    public Captcha() {
        params = new HashMap<>();
    }
}
