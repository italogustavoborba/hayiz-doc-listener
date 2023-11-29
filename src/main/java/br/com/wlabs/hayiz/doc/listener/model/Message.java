package br.com.wlabs.hayiz.doc.listener.model;

import br.com.wlabs.hayiz.doc.listener.enumerator.UFType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("serial")
@EqualsAndHashCode(callSuper = false)
public @Data
class Message {

    private String id;

    private String action;

    private String date;

    private UFType uf;

    private String city;

    private Map<String, Object> data;

}
