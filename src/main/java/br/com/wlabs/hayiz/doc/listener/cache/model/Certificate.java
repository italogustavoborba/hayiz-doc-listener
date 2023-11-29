package br.com.wlabs.hayiz.doc.listener.cache.model;

import lombok.*;

import java.io.InputStream;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("serial")
@EqualsAndHashCode(callSuper = false)
@Builder
public @Data
class Certificate {

    private LocalDateTime expiration;

    private byte[] stream;
}
