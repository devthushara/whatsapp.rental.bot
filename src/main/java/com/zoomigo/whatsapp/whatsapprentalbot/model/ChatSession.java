package com.zoomigo.whatsapp.whatsapprentalbot.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ChatSession {
    private String userId;
    private String state;
    private Map<String, String> data = new HashMap<>();
    private Instant lastUpdated = Instant.now();

    public ChatSession(String userId) {
        this.userId = userId;
        this.state = "ASK_NAME";
    }

    public String getUserId() {
        return userId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Map<String, String> getData() {
        return data;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void touch() {
        this.lastUpdated = Instant.now();
    }
}