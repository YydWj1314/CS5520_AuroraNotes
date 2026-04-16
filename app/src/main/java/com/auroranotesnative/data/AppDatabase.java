package com.auroranotesnative.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.auroranotesnative.model.Note;

@Database(entities = {Note.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN colorTag INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract NoteDao noteDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "aurora_notes_db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .allowMainThreadQueries() // todo: modify to
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}