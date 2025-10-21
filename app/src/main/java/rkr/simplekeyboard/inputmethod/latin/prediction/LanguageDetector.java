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
 * Detects the language of input text (English vs Kannada) for intelligent
 * suggestion filtering and ranking.
 */
public class LanguageDetector {

    /**
     * Supported languages for prediction
     */
    public enum Language {
        ENGLISH("en"),
        KANNADA("kn"),
        MIXED("mix");  // Code-switching (e.g., Kanglish)

        private final String code;

        Language(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public boolean isKannada() {
            return this == KANNADA || this == MIXED;
        }

        public boolean isEnglish() {
            return this == ENGLISH || this == MIXED;
        }
    }

    // Kannada Unicode range: U+0C80 to U+0CFF
    private static final char KANNADA_START = '\u0C80';
    private static final char KANNADA_END = '\u0CFF';

    /**
     * Detect the language of input text based on character composition.
     *
     * @param text The input text to analyze
     * @return The detected language
     */
    public static Language detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return Language.ENGLISH; // Default
        }

        int kannadaChars = 0;
        int latinChars = 0;
        int totalChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isKannadaChar(c)) {
                kannadaChars++;
                totalChars++;
            } else if (isLatinChar(c)) {
                latinChars++;
                totalChars++;
            }
            // Ignore spaces, punctuation, etc.
        }

        // Determine language based on character ratios
        if (totalChars == 0) {
            return Language.ENGLISH;
        }

        double kannadaRatio = (double) kannadaChars / totalChars;
        double latinRatio = (double) latinChars / totalChars;

        if (kannadaRatio > 0.7) {
            return Language.KANNADA;
        } else if (latinRatio > 0.7) {
            return Language.ENGLISH;
        } else if (kannadaChars > 0 && latinChars > 0) {
            return Language.MIXED;
        } else {
            return Language.ENGLISH; // Default fallback
        }
    }

    /**
     * Check if a character is in the Kannada Unicode block.
     */
    public static boolean isKannadaChar(char c) {
        return c >= KANNADA_START && c <= KANNADA_END;
    }

    /**
     * Check if a character is a Latin alphabet character.
     */
    public static boolean isLatinChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Detect language from a sequence of recent words (for context analysis).
     *
     * @param recentWords Array of recently typed words
     * @return Detected language based on recent context
     */
    public static Language detectContextLanguage(String[] recentWords) {
        if (recentWords == null || recentWords.length == 0) {
            return Language.ENGLISH;
        }

        int kannadaWords = 0;
        int englishWords = 0;

        for (String word : recentWords) {
            Language wordLang = detectLanguage(word);
            if (wordLang == Language.KANNADA) {
                kannadaWords++;
            } else if (wordLang == Language.ENGLISH) {
                englishWords++;
            }
        }

        if (kannadaWords > englishWords) {
            return Language.KANNADA;
        } else if (englishWords > kannadaWords) {
            return Language.ENGLISH;
        } else {
            return Language.MIXED;
        }
    }

    /**
     * Infer the expected language from the keyboard layout.
     * This helps determine which language suggestions to prioritize.
     *
     * @param layout The current keyboard layout
     * @return Expected primary language for this layout
     */
    public static Language getExpectedLanguage(LayoutDetector.KeyboardLayout layout) {
        switch (layout) {
            case QWERTY:
                return Language.ENGLISH;
            case PHONETIC:
                // Phonetic users may type in both languages
                return Language.MIXED;
            case STANDARD:
            case CUSTOM:
                return Language.KANNADA;
            default:
                return Language.ENGLISH;
        }
    }

    /**
     * Determine if bilingual suggestions should be shown for this layout.
     *
     * @param layout The keyboard layout
     * @return true if both English and Kannada suggestions should be provided
     */
    public static boolean shouldShowBilingualSuggestions(LayoutDetector.KeyboardLayout layout) {
        // Phonetic layout users often code-switch between English and Kannada
        return layout == LayoutDetector.KeyboardLayout.PHONETIC;
    }

    /**
     * Get the suggested language split for suggestion display.
     * For example, "3 Kannada : 2 English" for phonetic layout.
     *
     * @param layout The keyboard layout
     * @return Array of [kannadaCount, englishCount] for top suggestions
     */
    public static int[] getSuggestionLanguageSplit(LayoutDetector.KeyboardLayout layout) {
        switch (layout) {
            case QWERTY:
                return new int[]{0, 5};  // 0 Kannada, 5 English
            case PHONETIC:
                return new int[]{3, 2};  // 3 Kannada, 2 English
            case STANDARD:
            case CUSTOM:
                return new int[]{5, 0};  // 5 Kannada, 0 English
            default:
                return new int[]{0, 5};  // Default to English
        }
    }

    /**
     * Context tracking for language detection based on typing history.
     */
    public static class LanguageContext {
        private static final int MAX_HISTORY = 10;
        private final String[] wordHistory;
        private int historyIndex = 0;

        public LanguageContext() {
            this.wordHistory = new String[MAX_HISTORY];
        }

        /**
         * Add a word to the typing history.
         */
        public void addWord(String word) {
            if (word == null || word.isEmpty()) return;

            wordHistory[historyIndex] = word;
            historyIndex = (historyIndex + 1) % MAX_HISTORY;
        }

        /**
         * Get the current language context based on recent typing.
         */
        public Language getCurrentContext() {
            return detectContextLanguage(wordHistory);
        }

        /**
         * Clear the typing history (e.g., when switching to a new input field).
         */
        public void clear() {
            for (int i = 0; i < wordHistory.length; i++) {
                wordHistory[i] = null;
            }
            historyIndex = 0;
        }
    }
}
