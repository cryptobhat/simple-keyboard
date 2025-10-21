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

import java.util.HashMap;
import java.util.Map;

/**
 * Handles abbreviation expansion for common shortcuts.
 * Provides smart text expansion for frequently used phrases.
 */
public class AbbreviationExpander {

    private final Map<String, String> englishAbbreviations = new HashMap<>();
    private final Map<String, String> kannadaAbbreviations = new HashMap<>();
    private final Map<String, String> customAbbreviations = new HashMap<>();

    public AbbreviationExpander() {
        initializeDefaultAbbreviations();
    }

    /**
     * Initialize default abbreviations.
     */
    private void initializeDefaultAbbreviations() {
        // English abbreviations
        englishAbbreviations.put("btw", "by the way");
        englishAbbreviations.put("brb", "be right back");
        englishAbbreviations.put("lol", "laugh out loud");
        englishAbbreviations.put("omg", "oh my god");
        englishAbbreviations.put("idk", "I don't know");
        englishAbbreviations.put("imo", "in my opinion");
        englishAbbreviations.put("imho", "in my humble opinion");
        englishAbbreviations.put("tbh", "to be honest");
        englishAbbreviations.put("afaik", "as far as I know");
        englishAbbreviations.put("asap", "as soon as possible");
        englishAbbreviations.put("fyi", "for your information");
        englishAbbreviations.put("aka", "also known as");
        englishAbbreviations.put("atm", "at the moment");
        englishAbbreviations.put("eta", "estimated time of arrival");
        englishAbbreviations.put("rn", "right now");
        englishAbbreviations.put("dm", "direct message");
        englishAbbreviations.put("irl", "in real life");
        englishAbbreviations.put("ttyl", "talk to you later");
        englishAbbreviations.put("gtg", "got to go");
        englishAbbreviations.put("nvm", "never mind");
        englishAbbreviations.put("pls", "please");
        englishAbbreviations.put("plz", "please");
        englishAbbreviations.put("thx", "thanks");
        englishAbbreviations.put("ty", "thank you");
        englishAbbreviations.put("np", "no problem");
        englishAbbreviations.put("yw", "you're welcome");
        englishAbbreviations.put("msg", "message");
        englishAbbreviations.put("pic", "picture");
        englishAbbreviations.put("ppl", "people");
        englishAbbreviations.put("smh", "shaking my head");
        englishAbbreviations.put("wbu", "what about you");
        englishAbbreviations.put("hbu", "how about you");
        englishAbbreviations.put("jk", "just kidding");
        englishAbbreviations.put("bc", "because");
        englishAbbreviations.put("cuz", "because");
        englishAbbreviations.put("ofc", "of course");

        // Common Kannada abbreviations (transliterated)
        kannadaAbbreviations.put("ನಮ", "ನಮಸ್ಕಾರ");
        kannadaAbbreviations.put("ಧನ್ಯ", "ಧನ್ಯವಾದ");
        kannadaAbbreviations.put("ದಯ", "ದಯವಿಟ್ಟು");
        kannadaAbbreviations.put("ಕ್ಷಮ", "ಕ್ಷಮಿಸಿ");
    }

    /**
     * Check if a word is an abbreviation and get its expansion.
     *
     * @param word The word to check
     * @param language The language context
     * @return Expanded form, or null if not an abbreviation
     */
    public String getExpansion(String word, LanguageDetector.Language language) {
        if (word == null || word.isEmpty()) {
            return null;
        }

        String wordLower = word.toLowerCase();

        // Check custom abbreviations first (user-defined)
        if (customAbbreviations.containsKey(wordLower)) {
            return customAbbreviations.get(wordLower);
        }

        // Check language-specific abbreviations
        if (language == LanguageDetector.Language.KANNADA) {
            if (kannadaAbbreviations.containsKey(word)) {
                return kannadaAbbreviations.get(word);
            }
        } else {
            if (englishAbbreviations.containsKey(wordLower)) {
                return englishAbbreviations.get(wordLower);
            }
        }

        return null;
    }

    /**
     * Add a custom abbreviation.
     *
     * @param abbreviation The short form
     * @param expansion The expanded form
     */
    public void addCustomAbbreviation(String abbreviation, String expansion) {
        if (abbreviation != null && !abbreviation.isEmpty() &&
            expansion != null && !expansion.isEmpty()) {
            customAbbreviations.put(abbreviation.toLowerCase(), expansion);
        }
    }

    /**
     * Remove a custom abbreviation.
     *
     * @param abbreviation The abbreviation to remove
     */
    public void removeCustomAbbreviation(String abbreviation) {
        if (abbreviation != null) {
            customAbbreviations.remove(abbreviation.toLowerCase());
        }
    }

    /**
     * Check if a word is a known abbreviation.
     *
     * @param word The word to check
     * @return true if it's a known abbreviation
     */
    public boolean isAbbreviation(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        String wordLower = word.toLowerCase();
        return customAbbreviations.containsKey(wordLower) ||
               englishAbbreviations.containsKey(wordLower) ||
               kannadaAbbreviations.containsKey(word);
    }

    /**
     * Get all custom abbreviations.
     *
     * @return Map of custom abbreviations
     */
    public Map<String, String> getCustomAbbreviations() {
        return new HashMap<>(customAbbreviations);
    }

    /**
     * Clear all custom abbreviations.
     */
    public void clearCustomAbbreviations() {
        customAbbreviations.clear();
    }

    /**
     * Get count of all abbreviations.
     *
     * @return Total abbreviation count
     */
    public int getAbbreviationCount() {
        return englishAbbreviations.size() +
               kannadaAbbreviations.size() +
               customAbbreviations.size();
    }
}
