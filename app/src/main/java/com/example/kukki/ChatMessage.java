package com.example.kukki;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_ASSISTANT = 2;

    private final int type;
    private String text;

    public ChatMessage(int type, String text) {
        this.type = type;
        this.text = text;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
