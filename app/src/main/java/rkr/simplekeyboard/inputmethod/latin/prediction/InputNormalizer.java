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

import java.text.Normalizer;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.latin.utils.KannadaTransliterator;

/**
 * Normalizes input text from different keyboard layouts into canonical form
 * for consistent dictionary lookup and prediction.
 */
public class InputNormalizer {

    /**
     * Normalize input text based on keyboard layout.
     * Converts phonetic input to Kannada, normalizes Unicode forms, etc.
     *
     * @param input The raw input text
     * @param layout The current keyboard layout
     * @return Normalized text ready for dictionary lookup
     */
    public String normalize(String input, LayoutDetector.KeyboardLayout layout) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        switch (layout) {
            case PHONETIC:
                // Use existing transliterator for phonetic input
                return KannadaTransliterator.transliterate(input);

            case STANDARD:
            case CUSTOM:
                // Direct Kannada input - normalize Unicode composition
                return canonicalizeKannada(input);

            case QWERTY:
                // English input - lowercase normalization
                return input.toLowerCase(Locale.ENGLISH);

            default:
                return input;
        }
    }

    /**
     * Normalize Kannada Unicode to canonical form (NFC).
     * This ensures consistent character representation for dictionary lookup.
     *
     * @param text The Kannada text to normalize
     * @return Normalized Kannada text in NFC form
     */
    private String canonicalizeKannada(String text) {
        // Convert to NFC (Canonical Composition)
        // This combines base characters with combining marks into single code points
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /**
     * Normalize for case-insensitive comparison.
     * Used for English word matching.
     *
     * @param text The text to normalize
     * @return Lowercase text
     */
    public String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ENGLISH);
    }
}
