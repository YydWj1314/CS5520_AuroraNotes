package com.auroranotesnative.model;

import java.io.Serializable;

public class Note implements Serializable {
    private final String id;
    private String title;
    private String content;
    private boolean pinned;
    private String updatedAt;

    public Note(String id, String title, String content, boolean pinned, String updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
