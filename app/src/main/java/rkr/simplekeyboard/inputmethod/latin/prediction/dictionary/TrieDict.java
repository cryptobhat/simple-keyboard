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
