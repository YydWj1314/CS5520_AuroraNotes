package com.auroranotesnative.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "notes")
public class Note implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;   // int with auto-generation

    private String title;
    private String content;
    private boolean pinned;
    private long updatedAt;

    /** Accent color as ARGB; 0 means no tag. */
    @ColumnInfo(defaultValue = "0")
    private int colorTag;

    // Constructor
    public Note(String title, String content, boolean pinned, long updatedAt, int colorTag) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
        this.colorTag = colorTag;
    }

    // getter / setter
    public int getId() {
        return id;
    }

    public void setId(int id) {   // Room requires a setter
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

    public int getColorTag() {
        return colorTag;
    }

    public void setColorTag(int colorTag) {
        this.colorTag = colorTag;
    }
}