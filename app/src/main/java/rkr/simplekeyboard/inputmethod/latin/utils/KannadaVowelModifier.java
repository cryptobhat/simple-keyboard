/*
 * Copyright (C) 2025 Custom Kannada Keyboard
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
 * Helper class to handle Kannada vowel modifier transformations.
 *
 * In Kannada, when a consonant is followed by a vowel letter, it should be transformed
 * to the consonant + vowel sign (matra) form.
 *
 * Example: ಮ (MA) + ಅ (A) = ಮಾ (MAA)
 *         ರ (RA) + ಇ (I) = ರಿ (RI)
 */
public class KannadaVowelModifier {
    // Kannada consonants (vyanjana) - from ಕ to ಳ
    private static final char KANNADA_LETTER_KA = '\u0C95';
    private static final char KANNADA_LETTER_LLA = '\u0CB3';
    private static final char KANNADA_LETTER_FA = '\u0CAB'; // Extended consonant

    // Kannada independent vowels (swara) to vowel signs (matra) mapping
    private static final Map<Character, Character> VOWEL_TO_MATRA = new HashMap<>();

    // Virama (halant) - used to suppress inherent vowel
    private static final char KANNADA_SIGN_VIRAMA = '\u0CCD';

    static {
        // Map independent vowel letters to their corresponding vowel signs
        VOWEL_TO_MATRA.put('\u0C85', '\u0000'); // ಅ (A) -> remove (inherent vowel)
        VOWEL_TO_MATRA.put('\u0C86', '\u0CBE'); // ಆ (AA) -> ಾ (AA sign)
        VOWEL_TO_MATRA.put('\u0C87', '\u0CBF'); // ಇ (I) -> ಿ (I sign)
        VOWEL_TO_MATRA.put('\u0C88', '\u0CC0'); // ಈ (II) -> ೀ (II sign)
        VOWEL_TO_MATRA.put('\u0C89', '\u0CC1'); // ಉ (U) -> ು (U sign)
        VOWEL_TO_MATRA.put('\u0C8A', '\u0CC2'); // ಊ (UU) -> ೂ (UU sign)
        VOWEL_TO_MATRA.put('\u0C8B', '\u0CC3'); // ಋ (VOCALIC R) -> ೃ (VOCALIC R sign)
        VOWEL_TO_MATRA.put('\u0CE0', '\u0CC4'); // ೠ (VOCALIC RR) -> ೄ (VOCALIC RR sign)
        VOWEL_TO_MATRA.put('\u0C8C', '\u0CC4'); // ಌ (VOCALIC L) -> ೄ (VOCALIC L sign)
        VOWEL_TO_MATRA.put('\u0C8E', '\u0CC6'); // ಎ (E) -> ೆ (E sign)
        VOWEL_TO_MATRA.put('\u0C8F', '\u0CC7'); // ಏ (EE) -> ೇ (EE sign)
        VOWEL_TO_MATRA.put('\u0C90', '\u0CC8'); // ಐ (AI) -> ೈ (AI sign)
        VOWEL_TO_MATRA.put('\u0C92', '\u0CCA'); // ಒ (O) -> ೊ (O sign)
        VOWEL_TO_MATRA.put('\u0C93', '\u0CCB'); // ಓ (OO) -> ೋ (OO sign)
        VOWEL_TO_MATRA.put('\u0C94', '\u0CCC'); // ಔ (AU) -> ೌ (AU sign)
    }

    /**
     * Check if a character is a Kannada consonant.
     */
    public static boolean isKannadaConsonant(char c) {
        return (c >= KANNADA_LETTER_KA && c <= KANNADA_LETTER_LLA) ||
               c == KANNADA_LETTER_FA ||
               // Additional consonants
               c == '\u0C99' || // ಙ NGA
               c == '\u0C9A' || // ಚ CA
               c == '\u0C9B' || // ಛ CHA
               c == '\u0C9C' || // ಜ JA
               c == '\u0C9D' || // ಝ JHA
               c == '\u0C9E' || // ಞ NYA
               c == '\u0C9F' || // ಟ TTA
               c == '\u0CA0' || // ಠ TTHA
               c == '\u0CA1' || // ಡ DDA
               c == '\u0CA2' || // ಢ DDHA
               c == '\u0CA3' || // ಣ NNA
               c == '\u0CAD' || // ಭ BHA
               c == '\u0CB6' || // ಶ SHA
               c == '\u0CB7' || // ಷ SSA
               c == '\u0CB8' || // ಸ SA
               c == '\u0CB9';   // ಹ HA
    }

    /**
     * Check if a character is a Kannada independent vowel.
     */
    public static boolean isKannadaIndependentVowel(char c) {
        return VOWEL_TO_MATRA.containsKey(c);
    }

    /**
     * Get the vowel sign (matra) for an independent vowel.
     * Returns '\u0000' if the vowel represents the inherent 'a' sound.
     */
    public static char getVowelSign(char independentVowel) {
        Character matra = VOWEL_TO_MATRA.get(independentVowel);
        return matra != null ? matra : '\u0000';
    }

    /**
     * Process input for Kannada vowel modification.
     *
     * If the previous character is a consonant and the new character is an independent vowel,
     * this will replace the previous character with the consonant + vowel sign combination.
     *
     * @param previousChar The previously typed character (null if none)
     * @param newChar The newly typed character
     * @return A Result object containing whether modification occurred and the replacement string
     */
    public static Result processVowelModification(Character previousChar, char newChar) {
        // If there's no previous character or it's not a consonant, no modification
        if (previousChar == null || !isKannadaConsonant(previousChar)) {
            return new Result(false, null);
        }

        // If the new character is not an independent vowel, no modification
        if (!isKannadaIndependentVowel(newChar)) {
            return new Result(false, null);
        }

        // Get the vowel sign for the independent vowel
        char vowelSign = getVowelSign(newChar);

        // If the vowel is 'ಅ' (inherent A), the consonant stays as is (no sign needed)
        // Delete the vowel letter and keep just the consonant
        if (vowelSign == '\u0000') {
            return new Result(true, previousChar.toString());
        }

        // Otherwise, add virama to the consonant and then add the vowel sign
        // Wait, that's not correct. We just add the vowel sign directly to the consonant.
        // The consonant already has inherent 'a', so we replace it with the vowel sign.
        String result = previousChar.toString() + vowelSign;
        return new Result(true, result);
    }

    /**
     * Result class for vowel modification processing.
     */
    public static class Result {
        public final boolean modified;
        public final String replacement;

        public Result(boolean modified, String replacement) {
            this.modified = modified;
            this.replacement = replacement;
        }
    }
}
