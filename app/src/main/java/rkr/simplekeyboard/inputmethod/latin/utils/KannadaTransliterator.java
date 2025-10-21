/*
 * Copyright (C) 2025 Kannada Phonetic Keyboard
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

package rkr.simplekeyboard.inputmethod.latin.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Transliterator for converting romanized English input to Kannada script.
 * Based on ITRANS transliteration scheme with support for common Kannada phonetics.
 *
 * HOW TO WRITE COMPLEX WORDS:
 *
 * Basic Examples:
 *   ka -> ಕ (consonant with inherent 'a')
 *   ki -> ಕಿ (consonant with 'i' vowel sign)
 *   kaa or kA -> ಕಾ (consonant with long 'aa' vowel)
 *
 * Consonant Conjuncts (consonants without vowels):
 *   kannada -> ಕನ್ನಡ (double 'n' creates conjunct: ka-n-virama-na-da)
 *   namaskara -> ನಮಸ್ಕಾರ (sk conjunct: na-ma-s-virama-ka-aa-ra)
 *   krishna -> ಕೃಷ್ಣ (kr and shn conjuncts)
 *   vidya -> ವಿದ್ಯ (dy conjunct)
 *
 * Common Words:
 *   namaste -> ನಮಸ್ತೇ
 *   dhanyavaada -> ಧನ್ಯವಾದ
 *   pustaka -> ಪುಸ್ತಕ
 *   ganesha -> ಗಣೇಶ
 *   bangalore -> ಬಂಗಳೂರು (bengaLUru)
 *
 * Special Characters:
 *   M or . -> ಂ (anusvara)
 *   H -> ಃ (visarga)
 *
 * Vowel Modifiers:
 *   a/aa/i/ii/u/uu/e/ee/o/oo/ai/au
 *
 * Algorithm:
 *   - Reads consonant+vowel combinations
 *   - Adds virama (್) between consecutive consonants
 *   - Ends words with virama if final consonant has no vowel
 */
public class KannadaTransliterator {

    // Vowels (Independent form)
    private static final Map<String, String> VOWEL_MAP = new HashMap<>();

    // Consonants
    private static final Map<String, String> CONSONANT_MAP = new HashMap<>();

    // Vowel signs (Dependent/Matra form)
    private static final Map<String, String> VOWEL_SIGN_MAP = new HashMap<>();

    // Special characters
    private static final char VIRAMA = '\u0CCD'; // Halant/hasant - removes inherent 'a'
    private static final String ANUSVARA = "\u0C82"; // ಂ
    private static final String VISARGA = "\u0C83"; // ಃ

    static {
        // Independent vowels
        VOWEL_MAP.put("a", "ಅ");
        VOWEL_MAP.put("A", "ಆ");
        VOWEL_MAP.put("aa", "ಆ");
        VOWEL_MAP.put("i", "ಇ");
        VOWEL_MAP.put("I", "ಈ");
        VOWEL_MAP.put("ii", "ಈ");
        VOWEL_MAP.put("u", "ಉ");
        VOWEL_MAP.put("U", "ಊ");
        VOWEL_MAP.put("uu", "ಊ");
        VOWEL_MAP.put("R", "ಋ");
        VOWEL_MAP.put("RR", "ೠ");
        VOWEL_MAP.put("lR", "ಌ");
        VOWEL_MAP.put("e", "ಎ");
        VOWEL_MAP.put("E", "ಏ");
        VOWEL_MAP.put("ee", "ಏ");
        VOWEL_MAP.put("ai", "ಐ");
        VOWEL_MAP.put("o", "ಒ");
        VOWEL_MAP.put("O", "ಓ");
        VOWEL_MAP.put("oo", "ಓ");
        VOWEL_MAP.put("au", "ಔ");

        // Vowel signs (matras) - attached to consonants
        VOWEL_SIGN_MAP.put("a", ""); // Inherent vowel, no sign needed
        VOWEL_SIGN_MAP.put("A", "\u0CBE"); // ಾ
        VOWEL_SIGN_MAP.put("aa", "\u0CBE");
        VOWEL_SIGN_MAP.put("i", "\u0CBF"); // ಿ
        VOWEL_SIGN_MAP.put("I", "\u0CC0"); // ೀ
        VOWEL_SIGN_MAP.put("ii", "\u0CC0");
        VOWEL_SIGN_MAP.put("u", "\u0CC1"); // ು
        VOWEL_SIGN_MAP.put("U", "\u0CC2"); // ೂ
        VOWEL_SIGN_MAP.put("uu", "\u0CC2");
        VOWEL_SIGN_MAP.put("R", "\u0CC3"); // ೃ
        VOWEL_SIGN_MAP.put("RR", "\u0CC4"); // ೄ
        VOWEL_SIGN_MAP.put("e", "\u0CC6"); // ೆ
        VOWEL_SIGN_MAP.put("E", "\u0CC7"); // ೇ
        VOWEL_SIGN_MAP.put("ee", "\u0CC7");
        VOWEL_SIGN_MAP.put("ai", "\u0CC8"); // ೈ
        VOWEL_SIGN_MAP.put("o", "\u0CCA"); // ೊ
        VOWEL_SIGN_MAP.put("O", "\u0CCB"); // ೋ
        VOWEL_SIGN_MAP.put("oo", "\u0CCB");
        VOWEL_SIGN_MAP.put("au", "\u0CCC"); // ೌ

        // Consonants
        CONSONANT_MAP.put("k", "ಕ");
        CONSONANT_MAP.put("kh", "ಖ");
        CONSONANT_MAP.put("g", "ಗ");
        CONSONANT_MAP.put("gh", "ಘ");
        CONSONANT_MAP.put("~N", "ಙ");
        CONSONANT_MAP.put("ch", "ಚ");
        CONSONANT_MAP.put("Ch", "ಛ");
        CONSONANT_MAP.put("chh", "ಛ");
        CONSONANT_MAP.put("j", "ಜ");
        CONSONANT_MAP.put("jh", "ಝ");
        CONSONANT_MAP.put("~n", "ಞ");
        CONSONANT_MAP.put("T", "ಟ");
        CONSONANT_MAP.put("Th", "ಠ");
        CONSONANT_MAP.put("D", "ಡ");
        CONSONANT_MAP.put("Dh", "ಢ");
        CONSONANT_MAP.put("N", "ಣ");
        CONSONANT_MAP.put("t", "ತ");
        CONSONANT_MAP.put("th", "ಥ");
        CONSONANT_MAP.put("d", "ದ");
        CONSONANT_MAP.put("dh", "ಧ");
        CONSONANT_MAP.put("n", "ನ");
        CONSONANT_MAP.put("p", "ಪ");
        CONSONANT_MAP.put("ph", "ಫ");
        CONSONANT_MAP.put("b", "ಬ");
        CONSONANT_MAP.put("bh", "ಭ");
        CONSONANT_MAP.put("m", "ಮ");
        CONSONANT_MAP.put("y", "ಯ");
        CONSONANT_MAP.put("r", "ರ");
        CONSONANT_MAP.put("l", "ಲ");
        CONSONANT_MAP.put("v", "ವ");
        CONSONANT_MAP.put("w", "ವ"); // Alternative
        CONSONANT_MAP.put("sh", "ಶ");
        CONSONANT_MAP.put("Sh", "ಷ");
        CONSONANT_MAP.put("shh", "ಷ");
        CONSONANT_MAP.put("s", "ಸ");
        CONSONANT_MAP.put("h", "ಹ");
        CONSONANT_MAP.put("L", "ಳ");
        CONSONANT_MAP.put("ll", "ಳ");
        CONSONANT_MAP.put("zh", "ೞ");
        CONSONANT_MAP.put("f", "ಫ಼"); // Extended
        CONSONANT_MAP.put("z", "ಜ಼"); // Extended
        CONSONANT_MAP.put("x", "ಕ್ಷ"); // Conjunct
        CONSONANT_MAP.put("GY", "ಜ್ಞ"); // Conjunct
        CONSONANT_MAP.put("jny", "ಜ್ಞ"); // Conjunct alternative
    }

    /**
     * Transliterate a buffer of romanized text to Kannada.
     *
     * @param input The romanized input string (e.g., "namaskara")
     * @return The Kannada output string (e.g., "ನಮಸ್ಕಾರ")
     */
    public static String transliterate(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        int i = 0;
        boolean lastWasConsonant = false;

        while (i < input.length()) {
            String matchedConsonant = null;
            String matchedVowel = null;
            int consonantLength = 0;
            int vowelLength = 0;

            // Try to match consonant (check up to 3 characters)
            for (int len = Math.min(3, input.length() - i); len > 0; len--) {
                String substring = input.substring(i, i + len);
                if (CONSONANT_MAP.containsKey(substring)) {
                    matchedConsonant = CONSONANT_MAP.get(substring);
                    consonantLength = len;
                    break;
                }
            }

            // If consonant found, check for following vowel
            if (matchedConsonant != null) {
                int vowelStartPos = i + consonantLength;
                if (vowelStartPos < input.length()) {
                    // Look ahead for vowel (check up to 2 characters)
                    for (int vLen = Math.min(2, input.length() - vowelStartPos); vLen > 0; vLen--) {
                        String vowelSubstring = input.substring(vowelStartPos, vowelStartPos + vLen);
                        if (VOWEL_SIGN_MAP.containsKey(vowelSubstring)) {
                            matchedVowel = VOWEL_SIGN_MAP.get(vowelSubstring);
                            vowelLength = vLen;
                            break;
                        }
                    }
                }

                // Add virama to previous consonant if it had no vowel
                if (lastWasConsonant) {
                    output.append(VIRAMA);
                }

                // Append consonant
                output.append(matchedConsonant);

                // Append vowel sign if found and not empty (empty means inherent 'a')
                if (matchedVowel != null && !matchedVowel.isEmpty()) {
                    output.append(matchedVowel);
                    lastWasConsonant = false;
                } else if (matchedVowel != null && matchedVowel.isEmpty()) {
                    // Explicit 'a' was typed, consonant has inherent 'a'
                    lastWasConsonant = false;
                } else {
                    // No vowel found, mark that we have a pending consonant
                    lastWasConsonant = true;
                }

                i += consonantLength + vowelLength;
                continue;
            }

            // Try to match independent vowel (check up to 3 characters)
            String matchedIndependentVowel = null;
            int independentVowelLength = 0;
            for (int len = Math.min(3, input.length() - i); len > 0; len--) {
                String substring = input.substring(i, i + len);
                if (VOWEL_MAP.containsKey(substring)) {
                    matchedIndependentVowel = VOWEL_MAP.get(substring);
                    independentVowelLength = len;
                    break;
                }
            }

            if (matchedIndependentVowel != null) {
                // Add virama to previous consonant if needed
                if (lastWasConsonant) {
                    output.append(VIRAMA);
                    lastWasConsonant = false;
                }

                output.append(matchedIndependentVowel);
                i += independentVowelLength;
                continue;
            }

            // Handle special characters
            char c = input.charAt(i);
            if (c == 'M' || c == '.') {
                output.append(ANUSVARA);
                lastWasConsonant = false;
                i++;
            } else if (c == 'H') {
                output.append(VISARGA);
                lastWasConsonant = false;
                i++;
            } else if (c == ' ') {
                // Space ends any pending consonant with virama
                if (lastWasConsonant) {
                    output.append(VIRAMA);
                    lastWasConsonant = false;
                }
                output.append(' ');
                i++;
            } else {
                // Unknown character - pass through
                if (lastWasConsonant) {
                    output.append(VIRAMA);
                    lastWasConsonant = false;
                }
                output.append(c);
                i++;
            }
        }

        // Handle final consonant without vowel
        if (lastWasConsonant) {
            output.append(VIRAMA);
        }

        return output.toString();
    }

    /**
     * Check if a character could be part of a transliteration sequence.
     */
    public static boolean isTransliterationChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
               c == '~' || c == '.' || c == ' ';
    }

    /**
     * Check if a string is a valid start of a transliteration sequence.
     * Used for predictive buffering.
     */
    public static boolean isPotentialMatch(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }

        // Check if prefix matches beginning of any key
        for (String key : CONSONANT_MAP.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        for (String key : VOWEL_MAP.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Result class for transliteration with buffer management.
     */
    public static class TransliterationResult {
        public final String output;
        public final String remainingBuffer;

        public TransliterationResult(String output, String remainingBuffer) {
            this.output = output;
            this.remainingBuffer = remainingBuffer;
        }
    }
}
