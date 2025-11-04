package com.zoomigo.whatsapp.whatsapprentalbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientExecutorTest {

    @Test
    void conversationExecutor_properties() {
        HttpClientConfig cfg = new HttpClientConfig();
        ThreadPoolTaskExecutor exec = cfg.conversationExecutor();
        assertThat(exec.getCorePoolSize()).isEqualTo(5);
        assertThat(exec.getMaxPoolSize()).isEqualTo(20);
        assertThat(exec.getThreadNamePrefix()).isEqualTo("conv-");
    }
}

