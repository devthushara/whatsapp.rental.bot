package com.zoomigo.whatsapp.whatsapprentalbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableCaching
public class HttpClientConfig {

    @Value("${whatsapp.api-base-url:}")
    private String whatsappBaseProp;

    @Bean(name = "conversationExecutor")
    public ThreadPoolTaskExecutor conversationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(20);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("conv-");
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    @Bean
    public CacheManager cacheManager() {
        // Use a simple ConcurrentMapCacheManager for tests and small deployments.
        return new ConcurrentMapCacheManager("bikes", "promos");
    }

    @Bean
    public WebClient whatsappWebClient() {
        WebClient.Builder builder = WebClient.builder();
        String base = whatsappBaseProp;
        if (base != null && !base.isBlank()) builder.baseUrl(base);
        return builder.build();
    }
}
