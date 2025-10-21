/*
 * Copyright (C) 2025 Simple Keyboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.prediction.user;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database helper for user learning data.
 * Stores user-typed words with frequency and context for personalized predictions.
 */
public class UserDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "UserDatabaseHelper";

    private static final String DATABASE_NAME = "user_learning.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    public static final String TABLE_USER_WORDS = "user_words";
    public static final String TABLE_BIGRAMS = "user_bigrams";

    // User Words table columns
    public static final String COLUMN_WORD = "word";
    public static final String COLUMN_FREQUENCY = "frequency";
    public static final String COLUMN_LAST_USED = "last_used";
    public static final String COLUMN_LANGUAGE = "language";

    // Bigrams table columns
    public static final String COLUMN_WORD1 = "word1";
    public static final String COLUMN_WORD2 = "word2";
    public static final String COLUMN_BIGRAM_FREQUENCY = "frequency";
    public static final String COLUMN_BIGRAM_LAST_USED = "last_used";

    // Create table SQL statements
    private static final String CREATE_USER_WORDS_TABLE =
        "CREATE TABLE " + TABLE_USER_WORDS + " (" +
        COLUMN_WORD + " TEXT PRIMARY KEY, " +
        COLUMN_FREQUENCY + " INTEGER DEFAULT 1, " +
        COLUMN_LAST_USED + " INTEGER, " +
        COLUMN_LANGUAGE + " TEXT" +
        ")";

    private static final String CREATE_BIGRAMS_TABLE =
        "CREATE TABLE " + TABLE_BIGRAMS + " (" +
        COLUMN_WORD1 + " TEXT, " +
        COLUMN_WORD2 + " TEXT, " +
        COLUMN_BIGRAM_FREQUENCY + " INTEGER DEFAULT 1, " +
        COLUMN_BIGRAM_LAST_USED + " INTEGER, " +
        "PRIMARY KEY (" + COLUMN_WORD1 + ", " + COLUMN_WORD2 + ")" +
        ")";

    // Indexes for fast prefix search
    private static final String CREATE_WORD_INDEX =
        "CREATE INDEX idx_word_prefix ON " + TABLE_USER_WORDS +
        "(" + COLUMN_WORD + " COLLATE NOCASE)";

    private static final String CREATE_BIGRAM_INDEX =
        "CREATE INDEX idx_word1 ON " + TABLE_BIGRAMS +
        "(" + COLUMN_WORD1 + ")";

    public UserDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating user learning database");

        db.execSQL(CREATE_USER_WORDS_TABLE);
        db.execSQL(CREATE_BIGRAMS_TABLE);
        db.execSQL(CREATE_WORD_INDEX);
        db.execSQL(CREATE_BIGRAM_INDEX);

        Log.i(TAG, "User learning database created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion +
              " to " + newVersion);

        // For now, simple upgrade strategy: drop and recreate
        // In production, you'd want migration logic
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_WORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BIGRAMS);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
