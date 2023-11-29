package br.com.wlabs.hayiz.doc.listener.session.model;

import lombok.*;

import java.io.Serializable;
import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("serial")
@EqualsAndHashCode(callSuper = false)
public @Data
class Invoke implements Serializable {

    private String key;

    private String type;

    private Map<String, Object> parameters;
}
