package com.auroranotesnative.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.auroranotesnative.model.Note;

import java.util.List;

@Dao
public interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    List<Note> getAllNotes();

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    LiveData<List<Note>> observeAllNotes();

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    Note getNoteById(int id);

    @Query("SELECT * FROM notes WHERE lower(title) = lower(:title) LIMIT 1")
    Note getNoteByTitle(String title);

    @Insert
    long insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("DELETE FROM notes")
    void deleteAll();
}