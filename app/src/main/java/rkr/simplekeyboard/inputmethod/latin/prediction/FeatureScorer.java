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
 * Advanced feature-based scoring system for ranking suggestions.
 * Extracts multiple features and combines them intelligently.
 */
public class FeatureScorer {

    // Feature weights (can be adjusted based on user feedback)
    private double weightPrefixMatch = 2.0;
    private double weightExactMatch = 5.0;
    private double weightFrequency = 1.0;
    private double weightRecency = 1.5;
    private double weightLength = 0.5;
    private double weightContext = 2.5;
    private double weightUserLearned = 3.0;

    /**
     * Extract all features for a suggestion.
     */
    public static class SuggestionFeatures {
        public boolean isExactMatch;
        public boolean isPrefixMatch;
        public double prefixMatchRatio; // How much of the word matches the prefix
        public int editDistance;
        public int frequency;
        public double recencyScore; // 0.0 to 1.0
        public int wordLength;
        public int typedLength;
        public double lengthPenalty; // Penalty for very long words early in typing
        public boolean hasContextMatch; // Does it follow previous word well?
        public double contextScore;
        public boolean isUserLearned;
        public int userFrequency;
    }

    /**
     * Calculate comprehensive score for a suggestion.
     *
     * @param features Extracted features
     * @return Final score
     */
    public double calculateScore(SuggestionFeatures features) {
        double score = 0.0;

        // Exact match gets huge boost
        if (features.isExactMatch) {
            score += weightExactMatch * 1000;
        }

        // Prefix match boost (proportional to how much matches)
        if (features.isPrefixMatch) {
            score += weightPrefixMatch * features.prefixMatchRatio * 100;
        }

        // Frequency contribution (normalized)
        score += weightFrequency * Math.log(features.frequency + 1) * 10;

        // Recency boost
        score += weightRecency * features.recencyScore * 50;

        // Context matching (very important for next-word prediction)
        if (features.hasContextMatch) {
            score += weightContext * features.contextScore * 100;
        }

        // User learned words get priority
        if (features.isUserLearned) {
            score += weightUserLearned * Math.log(features.userFrequency + 1) * 20;
        }

        // Length penalty - don't suggest very long words when user typed little
        if (features.typedLength > 0) {
            double lengthRatio = (double) features.wordLength / features.typedLength;
            if (lengthRatio > 3.0) {
                // Word is much longer than typed - apply penalty
                features.lengthPenalty = 1.0 / lengthRatio;
                score *= features.lengthPenalty;
            }
        }

        // Edit distance penalty (for fuzzy matches)
        if (features.editDistance > 0) {
            double penalty = 1.0 - (features.editDistance * 0.2);
            score *= Math.max(0.3, penalty);
        }

        return score;
    }

    /**
     * Extract features from a suggestion.
     *
     * @param word The suggested word
     * @param typedWord What the user typed
     * @param frequency Word frequency
     * @param source Suggestion source
     * @return Extracted features
     */
    public SuggestionFeatures extractFeatures(String word, String typedWord,
                                              int frequency, Suggestion.Source source) {
        SuggestionFeatures features = new SuggestionFeatures();

        features.frequency = frequency;
        features.wordLength = word.length();
        features.typedLength = (typedWord != null) ? typedWord.length() : 0;
        features.isUserLearned = (source == Suggestion.Source.USER_LEARNED);

        if (typedWord != null && !typedWord.isEmpty()) {
            String wordLower = word.toLowerCase();
            String typedLower = typedWord.toLowerCase();

            // Check exact match
            features.isExactMatch = wordLower.equals(typedLower);

            // Check prefix match
            features.isPrefixMatch = wordLower.startsWith(typedLower);
            if (features.isPrefixMatch) {
                features.prefixMatchRatio = (double) typedLower.length() / wordLower.length();
            }

            // Calculate edit distance
            features.editDistance = editDistance(wordLower, typedLower);
        }

        return features;
    }

    /**
     * Calculate edit distance between two strings.
     */
    private int editDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // Quick optimization
        if (Math.abs(len1 - len2) > 3) {
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
     * Update weights based on user feedback (adaptive learning).
     *
     * @param featureType Which feature to adjust
     * @param adjustment Positive or negative adjustment
     */
    public void updateWeight(String featureType, double adjustment) {
        switch (featureType) {
            case "prefix":
                weightPrefixMatch += adjustment;
                break;
            case "exact":
                weightExactMatch += adjustment;
                break;
            case "frequency":
                weightFrequency += adjustment;
                break;
            case "recency":
                weightRecency += adjustment;
                break;
            case "context":
                weightContext += adjustment;
                break;
            case "user":
                weightUserLearned += adjustment;
                break;
        }

        // Ensure weights stay positive
        weightPrefixMatch = Math.max(0.1, weightPrefixMatch);
        weightExactMatch = Math.max(0.1, weightExactMatch);
        weightFrequency = Math.max(0.1, weightFrequency);
        weightRecency = Math.max(0.1, weightRecency);
        weightContext = Math.max(0.1, weightContext);
        weightUserLearned = Math.max(0.1, weightUserLearned);
    }

    /**
     * Get current weight settings.
     */
    public String getWeightsInfo() {
        return String.format("Weights: prefix=%.2f exact=%.2f freq=%.2f recency=%.2f context=%.2f user=%.2f",
            weightPrefixMatch, weightExactMatch, weightFrequency,
            weightRecency, weightContext, weightUserLearned);
    }
}
