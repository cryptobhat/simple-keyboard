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

import rkr.simplekeyboard.inputmethod.latin.Subtype;
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils;

/**
 * Detects the currently active keyboard layout and provides utilities
 * for layout-specific input handling.
 */
public class LayoutDetector {

    /**
     * Supported keyboard layouts for prediction engine
     */
    public enum KeyboardLayout {
        PHONETIC("kannada_phonetic"),      // English → Kannada transliteration (ITRANS-based)
        STANDARD("kannada"),                // Standard Kannada Inscript-like layout
        CUSTOM("kannada_custom"),           // Custom Kannada layout with vowel modifiers
        QWERTY("qwerty");                   // English QWERTY

        private final String layoutSetName;

        KeyboardLayout(String layoutSetName) {
            this.layoutSetName = layoutSetName;
        }

        public String getLayoutSetName() {
            return layoutSetName;
        }

        /**
         * Check if this layout outputs Kannada script
         */
        public boolean isKannadaLayout() {
            return this != QWERTY;
        }

        /**
         * Check if this layout uses phonetic input (Latin to Kannada)
         */
        public boolean isPhoneticLayout() {
            return this == PHONETIC;
        }
    }

    /**
     * Detect the keyboard layout from the current subtype.
     *
     * @param subtype The current input method subtype
     * @return The detected keyboard layout
     */
    public static KeyboardLayout detectLayout(final Subtype subtype) {
        if (subtype == null) {
            return KeyboardLayout.QWERTY; // Default fallback
        }

        final String layoutSet = subtype.getKeyboardLayoutSet();

        if (SubtypeLocaleUtils.LAYOUT_KANNADA_PHONETIC.equals(layoutSet)) {
            return KeyboardLayout.PHONETIC;
        } else if (SubtypeLocaleUtils.LAYOUT_KANNADA.equals(layoutSet)) {
            return KeyboardLayout.STANDARD;
        } else if (SubtypeLocaleUtils.LAYOUT_KANNADA_CUSTOM.equals(layoutSet)) {
            return KeyboardLayout.CUSTOM;
        } else if (SubtypeLocaleUtils.LAYOUT_QWERTY.equals(layoutSet)) {
            return KeyboardLayout.QWERTY;
        }

        // Default to QWERTY for unknown layouts
        return KeyboardLayout.QWERTY;
    }

    /**
     * Check if the current layout requires real-time transliteration
     * (i.e., Latin input needs to be converted to Kannada on-the-fly)
     *
     * @param layout The keyboard layout
     * @return true if transliteration is needed
     */
    public static boolean requiresTransliteration(KeyboardLayout layout) {
        return layout.isPhoneticLayout();
    }

    /**
     * Get the script/language of the layout's output
     *
     * @param layout The keyboard layout
     * @return "kn" for Kannada, "en" for English
     */
    public static String getLayoutLanguage(KeyboardLayout layout) {
        return layout.isKannadaLayout() ? "kn" : "en";
    }

    /**
     * Determine if predictions should be bilingual for this layout
     *
     * @param layout The keyboard layout
     * @return true if both Kannada and English suggestions should be shown
     */
    public static boolean isBilingualLayout(KeyboardLayout layout) {
        // Phonetic layout users may want English suggestions too
        return layout == KeyboardLayout.PHONETIC;
    }
}
