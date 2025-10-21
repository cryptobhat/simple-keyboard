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

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rkr.simplekeyboard.inputmethod.keyboard.Keyboard;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.define.DebugFlags;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils;
import rkr.simplekeyboard.inputmethod.latin.prediction.dictionary.TrieDict;
import rkr.simplekeyboard.inputmethod.latin.prediction.ngram.NgramModel;
import rkr.simplekeyboard.inputmethod.latin.prediction.user.UserLearningModel;

/**
 * Main prediction engine that orchestrates all suggestion components.
 * Provides bilingual (English + Kannada) predictions across all keyboard layouts.
 */
public class PredictionEngine {
    private static final String TAG = "PredictionEngine";

    private static final int MAX_SUGGESTIONS = 5;
    private static final int CACHE_SIZE = 100; // Cache last 100 queries

    // Dictionaries
    private TrieDict englishDict;
    private TrieDict kannadaDict;

    // Models
    private NgramModel ngramModel;
    private UserLearningModel userLearningModel;

    // Components
    private InputNormalizer inputNormalizer;
    private SuggestionRanker suggestionRanker;

    // Context tracking
    private String previousWord;
    private String secondPreviousWord;

    // State
    private boolean initialized = false;
    private final Context context;
    private final ExecutorService executor;

    // LRU cache for recent predictions
    private LruCache<String, List<Suggestion>> predictionCache;

    public PredictionEngine(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();

        this.inputNormalizer = new InputNormalizer();
        this.suggestionRanker = new SuggestionRanker();

        this.predictionCache = new LruCache<>(CACHE_SIZE);

        this.previousWord = null;
        this.secondPreviousWord = null;
    }

    /**
     * Initialize the prediction engine asynchronously.
     * Loads all dictionaries and models in background thread.
     */
    public void initializeAsync(final InitializationCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initialize();
                    if (callback != null) {
                        callback.onInitialized(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to initialize prediction engine", e);
                    if (callback != null) {
                        callback.onInitialized(false);
                    }
                }
            }
        });
    }

    /**
     * Synchronous initialization (blocking).
     */
    private void initialize() throws IOException {
        Log.i(TAG, "Initializing prediction engine...");

        AssetManager assets = context.getAssets();

        // Load English dictionary
        englishDict = new TrieDict();
        try {
            InputStream englishStream = assets.open("dictionaries/english_base.txt");
            englishDict.load(englishStream);
            englishStream.close();
            Log.i(TAG, "English dictionary loaded: " + englishDict.size() + " words");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load English dictionary", e);
        }

        // Load Kannada dictionary
        kannadaDict = new TrieDict();
        try {
            InputStream kannadaStream = assets.open("dictionaries/kannada_base.txt");
            kannadaDict.load(kannadaStream);
            kannadaStream.close();
            Log.i(TAG, "Kannada dictionary loaded: " + kannadaDict.size() + " words");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load Kannada dictionary", e);
        }

        // Initialize n-gram model (optional - may not have files yet)
        ngramModel = new NgramModel();
        try {
            // Try to load bigrams if available
            InputStream bigramStream = assets.open("dictionaries/english_bigrams.txt");
            ngramModel.loadBigrams(bigramStream);
            bigramStream.close();
            Log.i(TAG, "Bigrams loaded: " + ngramModel.getBigramCount());
        } catch (IOException e) {
            Log.i(TAG, "No bigram file found (optional)");
        }

        // Initialize user learning model
        userLearningModel = new UserLearningModel(context);
        Log.i(TAG, "User learning model initialized: " +
              userLearningModel.getWordCount() + " learned words");

        initialized = true;
        Log.i(TAG, "Prediction engine initialized successfully");
    }

    /**
     * Get word completion suggestions for current input.
     *
     * @param typedWord The word currently being typed
     * @param layout The current keyboard layout
     * @return List of suggestions
     */
    public List<Suggestion> getSuggestions(String typedWord,
                                           LayoutDetector.KeyboardLayout layout) {
        if (!initialized || typedWord == null || typedWord.isEmpty()) {
            return new ArrayList<>();
        }

        // Check cache first
        String cacheKey = typedWord + "_" + layout.name();
        List<Suggestion> cached = predictionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Normalize input based on layout
        String normalizedInput = inputNormalizer.normalize(typedWord, layout);

        // Collect suggestions from all sources
        List<Suggestion> allSuggestions = new ArrayList<>();

        // Determine language split based on layout
        int[] languageSplit = LanguageDetector.getSuggestionLanguageSplit(layout);
        int kannadaCount = languageSplit[0];
        int englishCount = languageSplit[1];

        // Get dictionary suggestions
        if (kannadaCount > 0 && kannadaDict != null) {
            List<String> kannadaWords = kannadaDict.getCompletions(
                normalizedInput, kannadaCount * 2
            );
            for (String word : kannadaWords) {
                int frequency = kannadaDict.getFrequency(word);
                allSuggestions.add(new Suggestion(
                    word,
                    frequency,
                    Suggestion.Source.DICTIONARY
                ));
            }
        }

        if (englishCount > 0 && englishDict != null) {
            List<String> englishWords = englishDict.getCompletions(
                typedWord.toLowerCase(), englishCount * 2
            );
            for (String word : englishWords) {
                int frequency = englishDict.getFrequency(word);
                allSuggestions.add(new Suggestion(
                    word,
                    frequency,
                    Suggestion.Source.DICTIONARY
                ));
            }
        }

        // Get user learning suggestions
        if (userLearningModel != null) {
            List<UserLearningModel.WordScore> userSuggestions =
                userLearningModel.getSuggestions(normalizedInput, MAX_SUGGESTIONS);

            for (UserLearningModel.WordScore ws : userSuggestions) {
                allSuggestions.add(new Suggestion(
                    ws.word,
                    ws.score,
                    Suggestion.Source.USER_LEARNED
                ));
            }
        }

        // Rank and filter suggestions
        List<Suggestion> rankedSuggestions = suggestionRanker.rankSuggestions(
            allSuggestions,
            typedWord,
            MAX_SUGGESTIONS * 2
        );

        // Apply language filtering
        List<Suggestion> finalSuggestions = suggestionRanker.filterByLanguage(
            rankedSuggestions,
            kannadaCount,
            englishCount
        );

        // Cache results
        predictionCache.put(cacheKey, finalSuggestions);

        return finalSuggestions;
    }

    /**
     * Get next-word predictions (for when user finished typing a word).
     *
     * @param layout The current keyboard layout
     * @return List of next-word suggestions
     */
    public List<Suggestion> getNextWordPredictions(LayoutDetector.KeyboardLayout layout) {
        if (!initialized) {
            return new ArrayList<>();
        }

        List<Suggestion> allSuggestions = new ArrayList<>();

        // Get n-gram predictions
        if (ngramModel != null && previousWord != null) {
            List<NgramModel.WordScore> ngramPredictions =
                ngramModel.getPredictions(secondPreviousWord, previousWord, MAX_SUGGESTIONS * 2);

            for (NgramModel.WordScore ws : ngramPredictions) {
                allSuggestions.add(new Suggestion(
                    ws.word,
                    ws.score,
                    Suggestion.Source.NGRAM
                ));
            }
        }

        // Get user bigram predictions
        if (userLearningModel != null && previousWord != null) {
            List<UserLearningModel.WordScore> bigramSuggestions =
                userLearningModel.getBigramSuggestions(previousWord, MAX_SUGGESTIONS);

            for (UserLearningModel.WordScore ws : bigramSuggestions) {
                allSuggestions.add(new Suggestion(
                    ws.word,
                    ws.score,
                    Suggestion.Source.USER_LEARNED
                ));
            }
        }

        // Rank suggestions
        List<Suggestion> rankedSuggestions = suggestionRanker.rankSuggestions(
            allSuggestions,
            null, // No typed word for next-word prediction
            MAX_SUGGESTIONS
        );

        // Apply language filtering
        int[] languageSplit = LanguageDetector.getSuggestionLanguageSplit(layout);
        return suggestionRanker.filterByLanguage(
            rankedSuggestions,
            languageSplit[0],
            languageSplit[1]
        );
    }

    /**
     * Update context when user commits a word.
     *
     * @param word The word that was committed
     */
    public void onWordCommitted(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }

        // Update context history
        secondPreviousWord = previousWord;
        previousWord = word;

        // Learn the word
        if (userLearningModel != null) {
            userLearningModel.addWord(word);

            // Learn bigram
            if (secondPreviousWord != null) {
                userLearningModel.addBigram(secondPreviousWord, word);
            }
        }

        // Clear prediction cache (context changed)
        predictionCache.evictAll();
    }

    /**
     * Reset context (e.g., when user moves to new field or sentence).
     */
    public void resetContext() {
        previousWord = null;
        secondPreviousWord = null;
        predictionCache.evictAll();
    }

    /**
     * Check if engine is initialized and ready.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get user learning model for direct access.
     *
     * @return UserLearningModel instance
     */
    public UserLearningModel getUserLearningModel() {
        return userLearningModel;
    }

    /**
     * Shutdown the prediction engine and release resources.
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down prediction engine");

        executor.shutdown();

        if (userLearningModel != null) {
            userLearningModel.close();
        }

        predictionCache.evictAll();
        initialized = false;
    }

    /**
     * Callback interface for async initialization.
     */
    public interface InitializationCallback {
        void onInitialized(boolean success);
    }
}
