package com.example.kukki;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {
    private String id;
    private String title;
    private long timestamp;
    private List<ChatMessage> messages;

    public ChatSession(String id, String title, long timestamp) {
        this.id = id;
        this.title = title;
        this.timestamp = timestamp;
        this.messages = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
