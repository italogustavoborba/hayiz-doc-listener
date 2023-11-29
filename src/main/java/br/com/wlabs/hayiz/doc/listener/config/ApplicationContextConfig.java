package br.com.wlabs.hayiz.doc.listener.config;

import br.com.wlabs.hayiz.doc.listener.properties.ApplicationContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationContextConfig {

    @Bean
    public ApplicationContextProvider applicationContextProvider() {
        return new ApplicationContextProvider();
    }
}
