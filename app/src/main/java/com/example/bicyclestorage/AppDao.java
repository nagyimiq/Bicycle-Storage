package com.example.bicyclestorage;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppDao {
    @Insert
    void insertNote(StorageNote note);

    @Delete
    void deleteNote(StorageNote note);

    @Query("SELECT * FROM storage_notes ORDER BY timestamp DESC")
    List<StorageNote> getAllNotes();

    @Query("SELECT * FROM storage_notes WHERE id = :noteId LIMIT 1")
    StorageNote getNoteById(int noteId);
}