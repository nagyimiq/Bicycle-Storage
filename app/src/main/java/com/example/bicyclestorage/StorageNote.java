package com.example.bicyclestorage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "storage_notes")
public class StorageNote {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String note;
    public double latitude;
    public double longitude;
    public long timestamp;

    public StorageNote(String note, double latitude, double longitude, long timestamp) {
        this.note = note;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}