package br.com.wlabs.hayiz.doc.listener.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableConfigurationProperties()
@ConfigurationProperties(prefix = "hayiz")
public @Data class Hayiz {

    private static Queue queue;

    private List<String> documents;

    private @Data static class Queue {

        private static String importer;

        private static String response;
    }
}
