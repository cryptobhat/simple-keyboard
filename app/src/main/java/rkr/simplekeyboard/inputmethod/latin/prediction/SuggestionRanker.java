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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks and merges suggestions from multiple sources.
 * Combines dictionary, n-gram, and user learning suggestions
 * into a final ranked list.
 */
public class SuggestionRanker {

    // Scoring weights for different sources
    private static final double WEIGHT_DICTIONARY = 1.0;
    private static final double WEIGHT_USER_LEARNING = 2.0; // User words get priority
    private static final double WEIGHT_NGRAM = 1.5;
    private static final double WEIGHT_EXACT_MATCH = 3.0;

    // Boost factors
    private static final double RECENCY_BOOST_MAX = 1.5;
    private static final double FREQUENCY_BOOST_MAX = 2.0;
    private static final double CONTEXT_BOOST = 2.0; // For n-gram matches

    /**
     * Rank and merge suggestions from multiple sources.
     *
     * @param suggestions List of suggestions from various sources
     * @param typedWord The word currently being typed (for exact match detection)
     * @param maxResults Maximum number of results to return
     * @return Ranked list of top suggestions
     */
    public List<Suggestion> rankSuggestions(List<Suggestion> suggestions,
                                            String typedWord,
                                            int maxResults) {
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }

        // Deduplicate and merge suggestions with same word
        Map<String, Suggestion> mergedMap = new HashMap<>();

        for (Suggestion suggestion : suggestions) {
            String word = suggestion.getWord();

            if (mergedMap.containsKey(word)) {
                // Merge scores
                Suggestion existing = mergedMap.get(word);
                double combinedScore = existing.getScore() + suggestion.getScore();

                // Keep the highest-priority source
                Suggestion.Source primarySource = getPrimarySource(
                    existing.getSource(),
                    suggestion.getSource()
                );

                Suggestion merged = new Suggestion(
                    word,
                    combinedScore,
                    primarySource
                );

                mergedMap.put(word, merged);
            } else {
                mergedMap.put(word, suggestion);
            }
        }

        // Convert to list
        List<Suggestion> mergedSuggestions = new ArrayList<>(mergedMap.values());

        // Apply additional scoring adjustments
        for (Suggestion suggestion : mergedSuggestions) {
            double finalScore = calculateFinalScore(suggestion, typedWord);
            suggestion.setScore(finalScore);
        }

        // Sort by score (descending)
        Collections.sort(mergedSuggestions);

        // Return top N results
        int resultCount = Math.min(maxResults, mergedSuggestions.size());
        return mergedSuggestions.subList(0, resultCount);
    }

    /**
     * Calculate final score with all adjustments applied.
     *
     * @param suggestion The suggestion to score
     * @param typedWord The currently typed word
     * @return Final score
     */
    private double calculateFinalScore(Suggestion suggestion, String typedWord) {
        double baseScore = suggestion.getScore();
        double multiplier = 1.0;

        // Apply source weight
        multiplier *= getSourceWeight(suggestion.getSource());

        // Exact match boost
        if (typedWord != null && !typedWord.isEmpty()) {
            if (suggestion.getWord().equalsIgnoreCase(typedWord)) {
                multiplier *= WEIGHT_EXACT_MATCH;
            } else if (suggestion.getWord().toLowerCase().startsWith(typedWord.toLowerCase())) {
                // Prefix match gets smaller boost
                multiplier *= 1.3;
            }
        }

        // Context boost (n-gram predictions)
        if (suggestion.getSource() == Suggestion.Source.NGRAM) {
            multiplier *= CONTEXT_BOOST;
        }

        return baseScore * multiplier;
    }

    /**
     * Get the scoring weight for a suggestion source.
     *
     * @param source The suggestion source
     * @return Weight multiplier
     */
    private double getSourceWeight(Suggestion.Source source) {
        switch (source) {
            case USER_LEARNED:
                return WEIGHT_USER_LEARNING;
            case NGRAM:
                return WEIGHT_NGRAM;
            case EXACT_MATCH:
                return WEIGHT_EXACT_MATCH;
            case DICTIONARY:
            case FREQUENCY:
            default:
                return WEIGHT_DICTIONARY;
        }
    }

    /**
     * Determine which source has higher priority when merging.
     *
     * @param source1 First source
     * @param source2 Second source
     * @return The higher priority source
     */
    private Suggestion.Source getPrimarySource(Suggestion.Source source1,
                                               Suggestion.Source source2) {
        // Priority order: EXACT_MATCH > USER_LEARNED > NGRAM > FREQUENCY > DICTIONARY

        if (source1 == Suggestion.Source.EXACT_MATCH ||
            source2 == Suggestion.Source.EXACT_MATCH) {
            return Suggestion.Source.EXACT_MATCH;
        }

        if (source1 == Suggestion.Source.USER_LEARNED ||
            source2 == Suggestion.Source.USER_LEARNED) {
            return Suggestion.Source.USER_LEARNED;
        }

        if (source1 == Suggestion.Source.NGRAM ||
            source2 == Suggestion.Source.NGRAM) {
            return Suggestion.Source.NGRAM;
        }

        if (source1 == Suggestion.Source.FREQUENCY ||
            source2 == Suggestion.Source.FREQUENCY) {
            return Suggestion.Source.FREQUENCY;
        }

        return Suggestion.Source.DICTIONARY;
    }

    /**
     * Filter suggestions by language based on keyboard layout.
     *
     * @param suggestions All suggestions
     * @param kannadaCount Number of Kannada suggestions to include
     * @param englishCount Number of English suggestions to include
     * @return Filtered list with specified language distribution
     */
    public List<Suggestion> filterByLanguage(List<Suggestion> suggestions,
                                             int kannadaCount,
                                             int englishCount) {
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Suggestion> kannadaSuggestions = new ArrayList<>();
        List<Suggestion> englishSuggestions = new ArrayList<>();

        // Split by language
        for (Suggestion suggestion : suggestions) {
            if (suggestion.isKannada()) {
                kannadaSuggestions.add(suggestion);
            } else {
                englishSuggestions.add(suggestion);
            }
        }

        // Sort each list
        Collections.sort(kannadaSuggestions);
        Collections.sort(englishSuggestions);

        // Combine with specified distribution
        List<Suggestion> result = new ArrayList<>();

        // Add Kannada suggestions
        int kannadaLimit = Math.min(kannadaCount, kannadaSuggestions.size());
        result.addAll(kannadaSuggestions.subList(0, kannadaLimit));

        // Add English suggestions
        int englishLimit = Math.min(englishCount, englishSuggestions.size());
        result.addAll(englishSuggestions.subList(0, englishLimit));

        // If one language didn't fill its quota, add more from the other
        int remaining = (kannadaCount + englishCount) - result.size();
        if (remaining > 0) {
            // Try to fill with remaining suggestions
            List<Suggestion> remainingKannada = kannadaSuggestions.subList(
                Math.min(kannadaLimit, kannadaSuggestions.size()),
                kannadaSuggestions.size()
            );
            List<Suggestion> remainingEnglish = englishSuggestions.subList(
                Math.min(englishLimit, englishSuggestions.size()),
                englishSuggestions.size()
            );

            // Combine and sort remaining
            List<Suggestion> remainingSuggestions = new ArrayList<>();
            remainingSuggestions.addAll(remainingKannada);
            remainingSuggestions.addAll(remainingEnglish);
            Collections.sort(remainingSuggestions);

            int toAdd = Math.min(remaining, remainingSuggestions.size());
            result.addAll(remainingSuggestions.subList(0, toAdd));
        }

        // Final sort by score
        Collections.sort(result);

        return result;
    }

    /**
     * Calculate edit distance (Levenshtein distance) between two strings.
     * Used for typo correction.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance
     */
    public static int editDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

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
     * Find suggestions similar to typed word (for typo correction).
     *
     * @param typedWord The word with potential typo
     * @param candidates List of candidate suggestions
     * @param maxDistance Maximum edit distance to consider
     * @return List of similar words
     */
    public List<Suggestion> findSimilarWords(String typedWord,
                                             List<Suggestion> candidates,
                                             int maxDistance) {
        if (typedWord == null || typedWord.isEmpty() || candidates == null) {
            return Collections.emptyList();
        }

        List<Suggestion> similar = new ArrayList<>();

        for (Suggestion candidate : candidates) {
            int distance = editDistance(
                typedWord.toLowerCase(),
                candidate.getWord().toLowerCase()
            );

            if (distance <= maxDistance) {
                // Adjust score based on edit distance
                double penalty = 1.0 - (distance * 0.3);
                double adjustedScore = candidate.getScore() * penalty;

                Suggestion adjusted = new Suggestion(
                    candidate.getWord(),
                    adjustedScore,
                    candidate.getSource()
                );

                similar.add(adjusted);
            }
        }

        Collections.sort(similar);
        return similar;
    }
}
