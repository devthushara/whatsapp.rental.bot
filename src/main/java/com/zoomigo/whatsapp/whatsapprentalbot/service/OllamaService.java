package com.zoomigo.whatsapp.whatsapprentalbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Deprecated
public class OllamaService {
    public OllamaService() {
        log.info("OllamaService is disabled in this build (LLM removed).");
    }

    /**
     * Previously used to extract booking JSON via Ollama; now disabled.
     * If called, return a simple null/empty behaviour or throw to make it obvious.
     */
    public String disabledMessage() {
        return "Ollama disabled";
    }
}
