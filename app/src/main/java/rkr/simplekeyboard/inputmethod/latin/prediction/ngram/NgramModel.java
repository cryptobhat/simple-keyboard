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

package rkr.simplekeyboard.inputmethod.latin.prediction.ngram;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * N-gram language model for context-aware next-word prediction.
 * Supports bigrams (2-word sequences) and trigrams (3-word sequences).
 */
public class NgramModel {
    private static final String TAG = "NgramModel";

    // Bigram: word1 -> (word2 -> frequency)
    private Map<String, Map<String, Integer>> bigrams;

    // Trigram: word1_word2 -> (word3 -> frequency)
    private Map<String, Map<String, Integer>> trigrams;

    private int bigramCount = 0;
    private int trigramCount = 0;

    public NgramModel() {
        this.bigrams = new HashMap<>();
        this.trigrams = new HashMap<>();
    }

    /**
     * Load bigrams from text file.
     * Format: word1<TAB>word2<TAB>frequency
     *
     * @param inputStream Input stream of bigram file
     * @throws IOException if reading fails
     */
    public void loadBigrams(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, "UTF-8")
        );

        String line;
        int count = 0;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                String word1 = parts[0].trim();
                String word2 = parts[1].trim();
                int frequency = 1;

                if (parts.length >= 3) {
                    try {
                        frequency = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        // Use default frequency
                    }
                }

                addBigram(word1, word2, frequency);
                count++;
            }
        }

        bigramCount = count;
        Log.i(TAG, "Loaded " + count + " bigrams");
    }

    /**
     * Load trigrams from text file.
     * Format: word1<TAB>word2<TAB>word3<TAB>frequency
     *
     * @param inputStream Input stream of trigram file
     * @throws IOException if reading fails
     */
    public void loadTrigrams(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, "UTF-8")
        );

        String line;
        int count = 0;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\t");
            if (parts.length >= 3) {
                String word1 = parts[0].trim();
                String word2 = parts[1].trim();
                String word3 = parts[2].trim();
                int frequency = 1;

                if (parts.length >= 4) {
                    try {
                        frequency = Integer.parseInt(parts[3].trim());
                    } catch (NumberFormatException e) {
                        // Use default frequency
                    }
                }

                addTrigram(word1, word2, word3, frequency);
                count++;
            }
        }

        trigramCount = count;
        Log.i(TAG, "Loaded " + count + " trigrams");
    }

    /**
     * Add a bigram to the model.
     *
     * @param word1 First word
     * @param word2 Second word
     * @param frequency Frequency count
     */
    public void addBigram(String word1, String word2, int frequency) {
        if (!bigrams.containsKey(word1)) {
            bigrams.put(word1, new HashMap<>());
        }
        bigrams.get(word1).put(word2, frequency);
    }

    /**
     * Add a trigram to the model.
     *
     * @param word1 First word
     * @param word2 Second word
     * @param word3 Third word
     * @param frequency Frequency count
     */
    public void addTrigram(String word1, String word2, String word3, int frequency) {
        String key = word1 + "_" + word2;
        if (!trigrams.containsKey(key)) {
            trigrams.put(key, new HashMap<>());
        }
        trigrams.get(key).put(word3, frequency);
    }

    /**
     * Get next-word predictions based on previous word (bigram).
     *
     * @param previousWord The previous word
     * @param maxResults Maximum number of results
     * @return List of predicted words with scores
     */
    public List<WordScore> getBigramPredictions(String previousWord, int maxResults) {
        List<WordScore> results = new ArrayList<>();

        if (previousWord == null || previousWord.isEmpty()) {
            return results;
        }

        // Normalize to lowercase for lookup
        String normalizedWord = previousWord.toLowerCase();

        Map<String, Integer> nextWords = bigrams.get(normalizedWord);
        if (nextWords == null || nextWords.isEmpty()) {
            return results;
        }

        // Convert to list and sort by frequency
        for (Map.Entry<String, Integer> entry : nextWords.entrySet()) {
            results.add(new WordScore(entry.getKey(), entry.getValue()));
        }

        Collections.sort(results, (a, b) -> Double.compare(b.score, a.score));

        // Return top N results
        return results.subList(0, Math.min(maxResults, results.size()));
    }

    /**
     * Get next-word predictions based on two previous words (trigram).
     *
     * @param word1 Second-to-last word
     * @param word2 Last word
     * @param maxResults Maximum number of results
     * @return List of predicted words with scores
     */
    public List<WordScore> getTrigramPredictions(String word1, String word2, int maxResults) {
        List<WordScore> results = new ArrayList<>();

        if (word1 == null || word2 == null ||
            word1.isEmpty() || word2.isEmpty()) {
            return results;
        }

        // Normalize to lowercase for lookup
        String key = word1.toLowerCase() + "_" + word2.toLowerCase();

        Map<String, Integer> nextWords = trigrams.get(key);
        if (nextWords == null || nextWords.isEmpty()) {
            return results;
        }

        // Convert to list and sort by frequency
        for (Map.Entry<String, Integer> entry : nextWords.entrySet()) {
            // Trigrams get higher score boost
            results.add(new WordScore(entry.getKey(), entry.getValue() * 1.5));
        }

        Collections.sort(results, (a, b) -> Double.compare(b.score, a.score));

        // Return top N results
        return results.subList(0, Math.min(maxResults, results.size()));
    }

    /**
     * Get combined predictions from both bigrams and trigrams.
     *
     * @param word1 Second-to-last word (can be null)
     * @param word2 Last word
     * @param maxResults Maximum number of results
     * @return List of predicted words with scores
     */
    public List<WordScore> getPredictions(String word1, String word2, int maxResults) {
        Map<String, Double> scoreMap = new HashMap<>();

        // Get trigram predictions if both words available
        if (word1 != null && !word1.isEmpty() &&
            word2 != null && !word2.isEmpty()) {

            List<WordScore> trigramResults = getTrigramPredictions(word1, word2, maxResults * 2);
            for (WordScore ws : trigramResults) {
                scoreMap.put(ws.word, ws.score);
            }
        }

        // Get bigram predictions
        if (word2 != null && !word2.isEmpty()) {
            List<WordScore> bigramResults = getBigramPredictions(word2, maxResults * 2);
            for (WordScore ws : bigramResults) {
                // Combine scores if word already exists from trigram
                scoreMap.merge(ws.word, ws.score, Double::sum);
            }
        }

        // Convert map to sorted list
        List<WordScore> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scoreMap.entrySet()) {
            results.add(new WordScore(entry.getKey(), entry.getValue()));
        }

        Collections.sort(results, (a, b) -> Double.compare(b.score, a.score));

        return results.subList(0, Math.min(maxResults, results.size()));
    }

    /**
     * Check if a bigram exists in the model.
     *
     * @param word1 First word
     * @param word2 Second word
     * @return true if bigram exists
     */
    public boolean containsBigram(String word1, String word2) {
        Map<String, Integer> nextWords = bigrams.get(word1.toLowerCase());
        return nextWords != null && nextWords.containsKey(word2.toLowerCase());
    }

    /**
     * Get the frequency of a specific bigram.
     *
     * @param word1 First word
     * @param word2 Second word
     * @return Frequency count, or 0 if not found
     */
    public int getBigramFrequency(String word1, String word2) {
        Map<String, Integer> nextWords = bigrams.get(word1.toLowerCase());
        if (nextWords == null) {
            return 0;
        }
        return nextWords.getOrDefault(word2.toLowerCase(), 0);
    }

    /**
     * Get total number of bigrams loaded.
     *
     * @return Bigram count
     */
    public int getBigramCount() {
        return bigramCount;
    }

    /**
     * Get total number of trigrams loaded.
     *
     * @return Trigram count
     */
    public int getTrigramCount() {
        return trigramCount;
    }

    /**
     * Clear all n-gram data.
     */
    public void clear() {
        bigrams.clear();
        trigrams.clear();
        bigramCount = 0;
        trigramCount = 0;
    }

    /**
     * Helper class to store word with score.
     */
    public static class WordScore {
        public final String word;
        public final double score;

        public WordScore(String word, double score) {
            this.word = word;
            this.score = score;
        }
    }
}
