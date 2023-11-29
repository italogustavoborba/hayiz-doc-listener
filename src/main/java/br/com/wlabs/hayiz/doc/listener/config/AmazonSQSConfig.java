package br.com.wlabs.hayiz.doc.listener.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.config.annotation.EnableSqs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration
@EnableSqs
@RequiredArgsConstructor
public class AmazonSQSConfig {

    private final AWSCredentialsProvider credentialsProvider;
    private final ConfigurableEnvironment configurableEnvironment;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    @Primary
    public AmazonSQSAsync amazonSQSAsync() {
        AmazonSQSAsync amazonSQSAsync = new AmazonSQSBufferedAsyncClient(AmazonSQSAsyncClient.asyncBuilder()
                .withCredentials(this.credentialsProvider)
                .withRegion(this.region)
                .build());
        return amazonSQSAsync;
    }

    /*@Bean
    public QueueMessagingTemplate queueMessagingTemplate() {
        return new QueueMessagingTemplate(amazonSQSAsync());
    }

    @Bean
    public SimpleMessageListenerContainerFactory simpleMessageListenerContainerFactory(AmazonSQSAsync amazonSQSAsync) {
        SimpleMessageListenerContainerFactory factory = new SimpleMessageListenerContainerFactory();
        factory.setAmazonSqs(amazonSQSAsync);

        if(Arrays.stream(this.configurableEnvironment.getActiveProfiles())
                .anyMatch(env -> (env.equalsIgnoreCase("dev")))) {
            factory.setAutoStartup(false);
        }
        factory.setAutoStartup(true);
        factory.setMaxNumberOfMessages(10);
        factory.setWaitTimeOut(20);
        factory.setBackOffTime(Long.valueOf(60000));
        //factory.setTaskExecutor(simpleAsyncTaskExecutor());

        return factory;
    }

    @Bean
    public MappingJackson2MessageConverter mappingJackson2MessageConverter() {
        MappingJackson2MessageConverter jackson2MessageConverter = new MappingJackson2MessageConverter();
        jackson2MessageConverter.setObjectMapper(objectMapper());
        return jackson2MessageConverter;
    }*/

    /*@Bean
    public SimpleAsyncTaskExecutor simpleAsyncTaskExecutor() {
        SimpleAsyncTaskExecutor simpleAsyncTaskExecutor = new SimpleAsyncTaskExecutor();
        simpleAsyncTaskExecutor.setConcurrencyLimit(30);
        return simpleAsyncTaskExecutor;
    }*/

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
        return mapper;
    }
}
