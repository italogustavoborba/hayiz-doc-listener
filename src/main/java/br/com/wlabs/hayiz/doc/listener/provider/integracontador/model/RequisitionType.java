package br.com.wlabs.hayiz.doc.listener.provider.integracontador.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@JsonFilter("attributeFilter")
public enum RequisitionType {

    Apoiar("Apoiar"),
    Emitir("Emitir");

    public final String displayName;

    private RequisitionType(String displayName) {
        this.displayName = displayName;
    }

    @JsonCreator
    public static RequisitionType forValue(String value) {
        return RequisitionType.valueOf(value);
    }

    @JsonValue
    public Map<String, String> jsonValue() {
        HashMap<String, String> map = new HashMap<>();
        map.put("name", this.name());
        map.put("displayName", this.displayName);
        return map;
    }
}
