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
    private static final int DATABASE_VERSION = 2; // Increased for trigram support

    // Table names
    public static final String TABLE_USER_WORDS = "user_words";
    public static final String TABLE_BIGRAMS = "user_bigrams";
    public static final String TABLE_TRIGRAMS = "user_trigrams";

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

    // Trigrams table columns
    public static final String COLUMN_WORD3 = "word3";
    public static final String COLUMN_TRIGRAM_FREQUENCY = "frequency";
    public static final String COLUMN_TRIGRAM_LAST_USED = "last_used";

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

    private static final String CREATE_TRIGRAMS_TABLE =
        "CREATE TABLE " + TABLE_TRIGRAMS + " (" +
        COLUMN_WORD1 + " TEXT, " +
        COLUMN_WORD2 + " TEXT, " +
        COLUMN_WORD3 + " TEXT, " +
        COLUMN_TRIGRAM_FREQUENCY + " INTEGER DEFAULT 1, " +
        COLUMN_TRIGRAM_LAST_USED + " INTEGER, " +
        "PRIMARY KEY (" + COLUMN_WORD1 + ", " + COLUMN_WORD2 + ", " + COLUMN_WORD3 + ")" +
        ")";

    // Indexes for fast prefix search and lookups
    private static final String CREATE_WORD_INDEX =
        "CREATE INDEX idx_word_prefix ON " + TABLE_USER_WORDS +
        "(" + COLUMN_WORD + " COLLATE NOCASE)";

    private static final String CREATE_WORD_FREQ_INDEX =
        "CREATE INDEX idx_word_freq ON " + TABLE_USER_WORDS +
        "(" + COLUMN_FREQUENCY + " DESC)";

    private static final String CREATE_BIGRAM_INDEX =
        "CREATE INDEX idx_word1 ON " + TABLE_BIGRAMS +
        "(" + COLUMN_WORD1 + ")";

    private static final String CREATE_BIGRAM_FREQ_INDEX =
        "CREATE INDEX idx_bigram_freq ON " + TABLE_BIGRAMS +
        "(" + COLUMN_WORD1 + ", " + COLUMN_BIGRAM_FREQUENCY + " DESC)";

    private static final String CREATE_TRIGRAM_INDEX =
        "CREATE INDEX idx_word12 ON " + TABLE_TRIGRAMS +
        "(" + COLUMN_WORD1 + ", " + COLUMN_WORD2 + ")";

    public UserDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating user learning database");

        // Create tables
        db.execSQL(CREATE_USER_WORDS_TABLE);
        db.execSQL(CREATE_BIGRAMS_TABLE);
        db.execSQL(CREATE_TRIGRAMS_TABLE);

        // Create indexes for performance
        db.execSQL(CREATE_WORD_INDEX);
        db.execSQL(CREATE_WORD_FREQ_INDEX);
        db.execSQL(CREATE_BIGRAM_INDEX);
        db.execSQL(CREATE_BIGRAM_FREQ_INDEX);
        db.execSQL(CREATE_TRIGRAM_INDEX);

        Log.i(TAG, "User learning database created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion +
              " to " + newVersion);

        if (oldVersion < 2) {
            // Add trigrams table and new indexes
            db.execSQL(CREATE_TRIGRAMS_TABLE);
            db.execSQL(CREATE_WORD_FREQ_INDEX);
            db.execSQL(CREATE_BIGRAM_FREQ_INDEX);
            db.execSQL(CREATE_TRIGRAM_INDEX);
            Log.i(TAG, "Added trigram support and performance indexes");
        }

        // For other upgrades, add migration logic here
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
