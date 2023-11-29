package br.com.wlabs.hayiz.doc.listener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadConfig {

    @Value("${hayiz.thread.max-pool-size:20}")
    private int poolSize;

    @Value("${hayiz.thread.timeout:10000}")
    private int timeout;

    @Bean(name = "threadPoolQueue")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 4);
        //executor.setAllowCoreThreadTimeOut(true);
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(100);
        executor.setThreadNamePrefix("SQSListener-");
        executor.setQueueCapacity(0);
        executor.setDaemon(true);
        //executor.setMaxPoolSize(poolSize);
        //executor.setQueueCapacity(0);
        //executor.setRejectedExecutionHandler(new BlockingTaskSubmissionPolicy(timeout));
        return executor;
    }
}
