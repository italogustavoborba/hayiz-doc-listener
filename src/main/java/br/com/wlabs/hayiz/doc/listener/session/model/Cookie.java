package br.com.wlabs.hayiz.doc.listener.session.model;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

import static java.util.Optional.ofNullable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("serial")
@EqualsAndHashCode(callSuper = false)
public @Data
class Cookie implements Serializable {

    private String name;

    private String value;

    private Long expiresAt;

    private String domain;

    private String path;

    public static Cookie of(okhttp3.Cookie cookie) {
        if (Objects.isNull(cookie)) {
            return null;
        }

        Cookie dto = new Cookie();

        ofNullable(cookie.name())
                .ifPresent(dto::setName);

        ofNullable(cookie.value())
                .ifPresent(dto::setValue);

        ofNullable(cookie.expiresAt())
                .ifPresent(dto::setExpiresAt);

        ofNullable(cookie.domain())
                .ifPresent(dto::setDomain);

        ofNullable(cookie.path())
                .ifPresent(dto::setPath);

        return dto;
    }

    public static okhttp3.Cookie of(Cookie dto) {
        if (Objects.isNull(dto)) {
            return null;
        }

        okhttp3.Cookie cookie = new okhttp3.Cookie.Builder()
                .name(dto.getName())
                .value(dto.getValue())
                .expiresAt(dto.getExpiresAt())
                .domain(dto.getDomain())
                .path(dto.getPath())
                .build();

        return cookie;
    }
}
