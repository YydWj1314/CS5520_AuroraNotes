package com.auroranotesnative.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "notes")
public class Note implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;   // ⭐ 改成 int + 自动生成

    private String title;
    private String content;
    private boolean pinned;
    private long updatedAt;

    // 构造函数
    public Note(String title, String content, boolean pinned, long updatedAt) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
    }

    // getter / setter
    public int getId() {
        return id;
    }

    public void setId(int id) {   // ⭐ Room 需要 setter
        this.id = id;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}