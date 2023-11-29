package br.com.wlabs.hayiz.doc.listener.session.model;

import com.fasterxml.jackson.annotation.JsonFilter;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonFilter("attributesFilter")
@SuppressWarnings("serial")
@EqualsAndHashCode(callSuper = false)
public @Data
class Session implements Serializable {

    private UUID id;

    private String key;

    private List<Cookie> data;
}
