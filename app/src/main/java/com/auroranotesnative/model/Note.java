package com.auroranotesnative.model;

import androidx.annotation.NonNull;
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

    /** Optional local image URI attached to note. */
    @ColumnInfo(defaultValue = "")
    @NonNull
    private String imageUri;

    /** Start of local calendar day for reminder; 0 = none. */
    @ColumnInfo(defaultValue = "0")
    private long dueDateMillis;

    /** Short line for home reminder banner (separate from title). */
    @ColumnInfo(defaultValue = "")
    @NonNull
    private String reminderText;

    // Constructor
    public Note(String title, String content, boolean pinned, long updatedAt, int colorTag,
                @NonNull String imageUri, long dueDateMillis, @NonNull String reminderText) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
        this.colorTag = colorTag;
        this.imageUri = imageUri;
        this.dueDateMillis = dueDateMillis;
        this.reminderText = reminderText;
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

    public String getImageUri() {
        return imageUri == null ? "" : imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri == null ? "" : imageUri;
    }

    public long getDueDateMillis() {
        return dueDateMillis;
    }

    public void setDueDateMillis(long dueDateMillis) {
        this.dueDateMillis = dueDateMillis;
    }

    @NonNull
    public String getReminderText() {
        return reminderText == null ? "" : reminderText;
    }

    public void setReminderText(String reminderText) {
        this.reminderText = reminderText == null ? "" : reminderText;
    }
}