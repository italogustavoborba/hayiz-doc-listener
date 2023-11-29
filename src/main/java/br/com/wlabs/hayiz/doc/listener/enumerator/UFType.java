package br.com.wlabs.hayiz.doc.listener.enumerator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@JsonFilter("attributeFilter")
public enum UFType {

    AN("Federal"),
    PE("Pernambuco"),
    PB("Paraíba"),
    AL("Alagoas");

    public final String displayName;

    private UFType(String displayName) {
        this.displayName = displayName;
    }

    @JsonCreator
    public static UFType forValue(String value) {
        return UFType.valueOf(value);
    }

    @JsonValue
    public Map<String, String> jsonValue() {
        HashMap<String, String> map = new HashMap<>();
        map.put("name", this.name());
        map.put("displayName", this.displayName);
        return map;
    }
}
