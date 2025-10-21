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

package rkr.simplekeyboard.inputmethod.latin.prediction;

/**
 * Represents a single word suggestion with metadata for ranking and display.
 */
public class Suggestion implements Comparable<Suggestion> {

    /**
     * Source of the suggestion (used for ranking and debugging)
     */
    public enum Source {
        DICTIONARY("dict"),      // From base dictionary
        USER_LEARNED("user"),    // From user's learned words
        NGRAM("ngram"),          // From n-gram context model
        FREQUENCY("freq"),       // From frequency table
        EXACT_MATCH("exact");    // Exact match with typed word

        private final String tag;

        Source(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }
    }

    private final String word;
    private double score;
    private final Source source;
    private final boolean isKannada;

    public Suggestion(String word, double score, Source source) {
        this.word = word;
        this.score = score;
        this.source = source;
        this.isKannada = isKannadaWord(word);
    }

    public String getWord() {
        return word;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void adjustScore(double delta) {
        this.score += delta;
    }

    public void multiplyScore(double factor) {
        this.score *= factor;
    }

    public Source getSource() {
        return source;
    }

    public boolean isKannada() {
        return isKannada;
    }

    /**
     * Check if a word contains Kannada characters
     */
    private static boolean isKannadaWord(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        // Kannada Unicode range: U+0C80 to U+0CFF
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= '\u0C80' && c <= '\u0CFF') {
                return true;
            }
        }
        return false;
    }

    /**
     * Compare suggestions by score (higher scores first)
     */
    @Override
    public int compareTo(Suggestion other) {
        return Double.compare(other.score, this.score); // Descending order
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Suggestion)) return false;
        Suggestion other = (Suggestion) obj;
        return word.equals(other.word);
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Suggestion{word='%s', score=%.2f, source=%s}",
                word, score, source.getTag());
    }

    /**
     * Builder for creating suggestions with fluent API
     */
    public static class Builder {
        private String word;
        private double score = 1.0;
        private Source source = Source.DICTIONARY;

        public Builder word(String word) {
            this.word = word;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Suggestion build() {
            if (word == null || word.isEmpty()) {
                throw new IllegalArgumentException("Suggestion word cannot be null or empty");
            }
            return new Suggestion(word, score, source);
        }
    }
}
