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

package rkr.simplekeyboard.inputmethod.latin.prediction.dictionary;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Trie-based dictionary for fast prefix matching and word completion.
 * Supports frequency-based ranking of suggestions.
 */
public class TrieDict {
    private static final String TAG = "TrieDict";

    private TrieNode root;
    private int wordCount = 0;

    /**
     * Node in the Trie structure
     */
    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isWordEnd = false;
        int frequency = 0;
    }

    public TrieDict() {
        this.root = new TrieNode();
    }

    /**
     * Load dictionary from text file (word\tfrequency format).
     *
     * @param inputStream Input stream of the dictionary file
     * @throws IOException if reading fails
     */
    public void load(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, "UTF-8")
        );

        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip empty lines and comments
            }

            String[] parts = line.split("\t");
            if (parts.length >= 1) {
                String word = parts[0].trim();
                int frequency = 1000; // Default frequency

                if (parts.length >= 2) {
                    try {
                        frequency = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Use default frequency
                    }
                }

                if (!word.isEmpty()) {
                    insert(word, frequency);
                    count++;
                }
            }
        }

        wordCount = count;
        Log.i(TAG, "Loaded " + count + " words into Trie");
    }

    /**
     * Insert a word with frequency into the Trie.
     *
     * @param word The word to insert
     * @param frequency The frequency/popularity of the word
     */
    public void insert(String word, int frequency) {
        TrieNode node = root;

        for (char c : word.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
        }

        node.isWordEnd = true;
        node.frequency = frequency;
    }

    /**
     * Get word completions for a given prefix.
     *
     * @param prefix The prefix to search for
     * @param maxResults Maximum number of results to return
     * @return List of word suggestions sorted by frequency
     */
    public List<String> getCompletions(String prefix, int maxResults) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }

        TrieNode node = findNode(prefix);
        if (node == null) {
            return Collections.emptyList();
        }

        // Collect words with their frequencies
        List<WordFreq> results = new ArrayList<>();
        collectWords(node, prefix, results, maxResults * 3); // Collect extra for sorting

        // Sort by frequency (descending) and return top N
        Collections.sort(results, (a, b) -> b.frequency - a.frequency);

        List<String> words = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, results.size()); i++) {
            words.add(results.get(i).word);
        }

        return words;
    }

    /**
     * Check if a word exists in the dictionary.
     *
     * @param word The word to check
     * @return true if the word exists
     */
    public boolean contains(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isWordEnd;
    }

    /**
     * Get the frequency of a word.
     *
     * @param word The word to look up
     * @return The frequency, or 0 if word not found
     */
    public int getFrequency(String word) {
        TrieNode node = findNode(word);
        return (node != null && node.isWordEnd) ? node.frequency : 0;
    }

    /**
     * Find the trie node for a given prefix.
     *
     * @param prefix The prefix to search for
     * @return The trie node, or null if not found
     */
    private TrieNode findNode(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /**
     * Recursively collect all words from a trie node.
     *
     * @param node The starting node
     * @param prefix The current prefix
     * @param results List to collect results
     * @param limit Maximum number of words to collect
     */
    private void collectWords(TrieNode node, String prefix,
                               List<WordFreq> results, int limit) {
        if (results.size() >= limit) {
            return;
        }

        if (node.isWordEnd) {
            results.add(new WordFreq(prefix, node.frequency));
        }

        // Traverse children
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectWords(entry.getValue(),
                        prefix + entry.getKey(),
                        results, limit);
        }
    }

    /**
     * Get fuzzy matches for a word (typo correction).
     * Uses edit distance to find similar words.
     *
     * @param word The word to match (potentially misspelled)
     * @param maxDistance Maximum edit distance (1-2 recommended)
     * @param maxResults Maximum number of results
     * @return List of similar words with frequencies
     */
    public List<WordFreq> getFuzzyMatches(String word, int maxDistance, int maxResults) {
        if (word == null || word.isEmpty() || maxDistance < 1) {
            return Collections.emptyList();
        }

        List<WordFreq> allWords = new ArrayList<>();
        collectAllWords(root, "", allWords);

        List<WordFreq> matches = new ArrayList<>();
        for (WordFreq candidate : allWords) {
            int distance = editDistance(word, candidate.word);
            if (distance <= maxDistance && distance > 0) {
                // Adjust frequency based on edit distance
                int adjustedFreq = (int) (candidate.frequency * (1.0 - (distance * 0.3)));
                matches.add(new WordFreq(candidate.word, adjustedFreq));
            }
        }

        // Sort by adjusted frequency
        Collections.sort(matches, (a, b) -> b.frequency - a.frequency);

        return matches.subList(0, Math.min(maxResults, matches.size()));
    }

    /**
     * Get words similar to prefix using keyboard-aware fuzzy matching.
     * Better for typos where adjacent keys are hit.
     *
     * @param prefix The typed prefix
     * @param maxResults Maximum results
     * @return Similar words
     */
    public List<String> getFuzzyCompletions(String prefix, int maxResults) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }

        // First try exact prefix match
        List<String> exactMatches = getCompletions(prefix, maxResults);
        if (exactMatches.size() >= maxResults) {
            return exactMatches;
        }

        // Add fuzzy matches if needed
        Set<String> resultSet = new LinkedHashSet<>(exactMatches);

        if (prefix.length() >= 3) {
            List<WordFreq> fuzzyMatches = getFuzzyMatches(prefix, 1, maxResults * 2);
            for (WordFreq match : fuzzyMatches) {
                resultSet.add(match.word);
                if (resultSet.size() >= maxResults) {
                    break;
                }
            }
        }

        return new ArrayList<>(resultSet);
    }

    /**
     * Calculate edit distance (Levenshtein) between two strings.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance
     */
    private int editDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // Optimize: if length difference is too large, skip calculation
        if (Math.abs(len1 - len2) > 2) {
            return Integer.MAX_VALUE;
        }

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * Collect all words from the trie (for fuzzy matching).
     *
     * @param node Starting node
     * @param prefix Current prefix
     * @param results List to collect results
     */
    private void collectAllWords(TrieNode node, String prefix, List<WordFreq> results) {
        if (node.isWordEnd) {
            results.add(new WordFreq(prefix, node.frequency));
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectAllWords(entry.getValue(), prefix + entry.getKey(), results);
        }
    }

    /**
     * Get the total number of words in the dictionary.
     *
     * @return Word count
     */
    public int size() {
        return wordCount;
    }

    /**
     * Helper class to store word with frequency
     */
    static class WordFreq {
        String word;
        int frequency;

        WordFreq(String w, int f) {
            word = w;
            frequency = f;
        }
    }
}
