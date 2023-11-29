package br.com.wlabs.hayiz.doc.listener.enumerator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@JsonFilter("attributeFilter")
public enum SessionType {

    E_CAC("e-CAC"),
    SEFAZ_PE("e-Fisco - Sefaz PE");

    public final String displayName;

    private SessionType(String displayName) {
        this.displayName = displayName;
    }

    @JsonCreator
    public static SessionType forValue(String value) {
        return SessionType.valueOf(value);
    }

    @JsonValue
    public Map<String, String> jsonValue() {
        HashMap<String, String> map = new HashMap<>();
        map.put("name", this.name());
        map.put("displayName", this.displayName);
        return map;
    }
}
