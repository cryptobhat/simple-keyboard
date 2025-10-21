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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.prediction.LanguageDetector;

/**
 * User learning model that tracks typed words and bigrams.
 * Provides personalized suggestions based on user's typing history.
 */
public class UserLearningModel {
    private static final String TAG = "UserLearningModel";

    private final UserDatabaseHelper dbHelper;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int PRUNE_THRESHOLD_DAYS = 90;
    private static final long DAY_IN_MILLIS = 86400000L;

    public UserLearningModel(Context context) {
        this.dbHelper = new UserDatabaseHelper(context);
    }

    /**
     * Record a user-typed word with automatic frequency increment.
     *
     * @param word The word typed by the user
     */
    public void addWord(String word) {
        if (word == null || word.isEmpty() || word.length() < 2) {
            return; // Skip very short words
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long timestamp = System.currentTimeMillis();

        // Detect language
        LanguageDetector.Language language = LanguageDetector.detectLanguage(word);
        String languageCode = language.getCode();

        try {
            db.beginTransaction();

            // Check if word exists
            Cursor cursor = db.query(
                UserDatabaseHelper.TABLE_USER_WORDS,
                new String[]{UserDatabaseHelper.COLUMN_FREQUENCY},
                UserDatabaseHelper.COLUMN_WORD + " = ?",
                new String[]{word},
                null, null, null
            );

            ContentValues values = new ContentValues();
            values.put(UserDatabaseHelper.COLUMN_WORD, word);
            values.put(UserDatabaseHelper.COLUMN_LAST_USED, timestamp);
            values.put(UserDatabaseHelper.COLUMN_LANGUAGE, languageCode);

            if (cursor.moveToFirst()) {
                // Word exists - increment frequency
                int currentFrequency = cursor.getInt(0);
                values.put(UserDatabaseHelper.COLUMN_FREQUENCY, currentFrequency + 1);

                db.update(
                    UserDatabaseHelper.TABLE_USER_WORDS,
                    values,
                    UserDatabaseHelper.COLUMN_WORD + " = ?",
                    new String[]{word}
                );
            } else {
                // New word
                values.put(UserDatabaseHelper.COLUMN_FREQUENCY, 1);
                db.insert(UserDatabaseHelper.TABLE_USER_WORDS, null, values);
            }

            cursor.close();
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error adding word: " + word, e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Record a bigram (word pair) for context-aware prediction.
     *
     * @param word1 The first word (previous word)
     * @param word2 The second word (current word)
     */
    public void addBigram(String word1, String word2) {
        if (word1 == null || word2 == null ||
            word1.isEmpty() || word2.isEmpty()) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long timestamp = System.currentTimeMillis();

        try {
            db.beginTransaction();

            // Check if bigram exists
            Cursor cursor = db.query(
                UserDatabaseHelper.TABLE_BIGRAMS,
                new String[]{UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY},
                UserDatabaseHelper.COLUMN_WORD1 + " = ? AND " +
                UserDatabaseHelper.COLUMN_WORD2 + " = ?",
                new String[]{word1, word2},
                null, null, null
            );

            ContentValues values = new ContentValues();
            values.put(UserDatabaseHelper.COLUMN_WORD1, word1);
            values.put(UserDatabaseHelper.COLUMN_WORD2, word2);
            values.put(UserDatabaseHelper.COLUMN_BIGRAM_LAST_USED, timestamp);

            if (cursor.moveToFirst()) {
                // Bigram exists - increment frequency
                int currentFrequency = cursor.getInt(0);
                values.put(UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY, currentFrequency + 1);

                db.update(
                    UserDatabaseHelper.TABLE_BIGRAMS,
                    values,
                    UserDatabaseHelper.COLUMN_WORD1 + " = ? AND " +
                    UserDatabaseHelper.COLUMN_WORD2 + " = ?",
                    new String[]{word1, word2}
                );
            } else {
                // New bigram
                values.put(UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY, 1);
                db.insert(UserDatabaseHelper.TABLE_BIGRAMS, null, values);
            }

            cursor.close();
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error adding bigram: " + word1 + " -> " + word2, e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Get user-learned word suggestions for a given prefix.
     *
     * @param prefix The prefix to match
     * @param maxResults Maximum number of results
     * @return List of word suggestions with frequencies
     */
    public List<WordScore> getSuggestions(String prefix, int maxResults) {
        List<WordScore> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return results;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Query words starting with prefix, ordered by frequency * recency
        String query = "SELECT " +
            UserDatabaseHelper.COLUMN_WORD + ", " +
            UserDatabaseHelper.COLUMN_FREQUENCY + ", " +
            UserDatabaseHelper.COLUMN_LAST_USED +
            " FROM " + UserDatabaseHelper.TABLE_USER_WORDS +
            " WHERE " + UserDatabaseHelper.COLUMN_WORD + " LIKE ? COLLATE NOCASE" +
            " ORDER BY " + UserDatabaseHelper.COLUMN_FREQUENCY + " DESC, " +
            UserDatabaseHelper.COLUMN_LAST_USED + " DESC" +
            " LIMIT ?";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{prefix + "%", String.valueOf(maxResults)});

            while (cursor.moveToNext()) {
                String word = cursor.getString(0);
                int frequency = cursor.getInt(1);
                long lastUsed = cursor.getLong(2);

                // Calculate recency boost (words used recently get higher scores)
                long ageInDays = (System.currentTimeMillis() - lastUsed) / DAY_IN_MILLIS;
                double recencyBoost = Math.max(0.1, 1.0 - (ageInDays / 90.0));

                double score = frequency * recencyBoost;
                results.add(new WordScore(word, score, frequency));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting suggestions for prefix: " + prefix, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    /**
     * Get bigram-based next-word suggestions.
     *
     * @param previousWord The previous word for context
     * @param maxResults Maximum number of results
     * @return List of next-word suggestions
     */
    public List<WordScore> getBigramSuggestions(String previousWord, int maxResults) {
        List<WordScore> results = new ArrayList<>();
        if (previousWord == null || previousWord.isEmpty()) {
            return results;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT " +
            UserDatabaseHelper.COLUMN_WORD2 + ", " +
            UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY + ", " +
            UserDatabaseHelper.COLUMN_BIGRAM_LAST_USED +
            " FROM " + UserDatabaseHelper.TABLE_BIGRAMS +
            " WHERE " + UserDatabaseHelper.COLUMN_WORD1 + " = ?" +
            " ORDER BY " + UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY + " DESC" +
            " LIMIT ?";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{previousWord, String.valueOf(maxResults)});

            while (cursor.moveToNext()) {
                String word = cursor.getString(0);
                int frequency = cursor.getInt(1);
                long lastUsed = cursor.getLong(2);

                // Calculate recency boost
                long ageInDays = (System.currentTimeMillis() - lastUsed) / DAY_IN_MILLIS;
                double recencyBoost = Math.max(0.1, 1.0 - (ageInDays / 90.0));

                double score = frequency * recencyBoost * 1.5; // Bigrams get 1.5x boost
                results.add(new WordScore(word, score, frequency));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting bigram suggestions for: " + previousWord, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    /**
     * Check if a word exists in user's learned vocabulary.
     *
     * @param word The word to check
     * @return true if the word exists
     */
    public boolean containsWord(String word) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(
                UserDatabaseHelper.TABLE_USER_WORDS,
                new String[]{UserDatabaseHelper.COLUMN_WORD},
                UserDatabaseHelper.COLUMN_WORD + " = ?",
                new String[]{word},
                null, null, null
            );

            return cursor.moveToFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking word: " + word, e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Prune old entries to keep database size manageable.
     * Removes words not used in the last 90 days with low frequency.
     */
    public void pruneOldEntries() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long cutoffTime = System.currentTimeMillis() - (PRUNE_THRESHOLD_DAYS * DAY_IN_MILLIS);

        try {
            db.beginTransaction();

            // Delete old, low-frequency words
            int deletedWords = db.delete(
                UserDatabaseHelper.TABLE_USER_WORDS,
                UserDatabaseHelper.COLUMN_LAST_USED + " < ? AND " +
                UserDatabaseHelper.COLUMN_FREQUENCY + " < 3",
                new String[]{String.valueOf(cutoffTime)}
            );

            // Delete old bigrams
            int deletedBigrams = db.delete(
                UserDatabaseHelper.TABLE_BIGRAMS,
                UserDatabaseHelper.COLUMN_BIGRAM_LAST_USED + " < ? AND " +
                UserDatabaseHelper.COLUMN_BIGRAM_FREQUENCY + " < 2",
                new String[]{String.valueOf(cutoffTime)}
            );

            db.setTransactionSuccessful();

            Log.i(TAG, "Pruned " + deletedWords + " words and " +
                  deletedBigrams + " bigrams");
        } catch (Exception e) {
            Log.e(TAG, "Error pruning old entries", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Clear all user learning data.
     */
    public void clearAllData() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.beginTransaction();
            db.delete(UserDatabaseHelper.TABLE_USER_WORDS, null, null);
            db.delete(UserDatabaseHelper.TABLE_BIGRAMS, null, null);
            db.setTransactionSuccessful();
            Log.i(TAG, "Cleared all user learning data");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing data", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Get total word count in user's vocabulary.
     *
     * @return Total number of learned words
     */
    public int getWordCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + UserDatabaseHelper.TABLE_USER_WORDS,
                null
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting word count", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }

    /**
     * Close database resources.
     */
    public void close() {
        dbHelper.close();
    }

    /**
     * Helper class to store word with score and frequency.
     */
    public static class WordScore {
        public final String word;
        public final double score;
        public final int frequency;

        public WordScore(String word, double score, int frequency) {
            this.word = word;
            this.score = score;
            this.frequency = frequency;
        }
    }
}
