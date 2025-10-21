# Bilingual Prediction Engine - English + Kannada

## Overview

This document extends the prediction engine to provide **full English and Kannada prediction support** with intelligent language detection and context-aware suggestions.

---

## Enhanced Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Input Detection Layer                   │
│  • Detect script (Latin vs Kannada Unicode)            │
│  • Detect keyboard layout                               │
│  • Language inference from context                      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Dual-Language Engine Core                   │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │  English Engine  │      │  Kannada Engine  │        │
│  │  • 200k words    │      │  • 50k words     │        │
│  │  • Bigrams       │      │  • Bigrams       │        │
│  │  • User learning │      │  • User learning │        │
│  └──────────────────┘      └──────────────────┘        │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│            Smart Language Mixing                         │
│  • Show both languages for code-switching               │
│  • Context-aware language prioritization                │
│  • Phonetic layout → Show both Kannada + English       │
└────────────────────┬────────────────────────────────────┘
                     │
                  Output
```

---

## Key Features

### 1. Automatic Language Detection

```java
// LanguageDetector.java
public class LanguageDetector {

    public enum Language {
        ENGLISH("en"),
        KANNADA("kn"),
        MIXED("mix");  // Code-switching (Kanglish)

        private final String code;
        Language(String code) { this.code = code; }
    }

    /**
     * Detect language of input text
     */
    public static Language detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return Language.ENGLISH;
        }

        int kannadaChars = 0;
        int latinChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0C80' && c <= '\u0CFF') {
                kannadaChars++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latinChars++;
            }
        }

        // Determine dominant language
        if (kannadaChars > latinChars) {
            return Language.KANNADA;
        } else if (latinChars > kannadaChars) {
            return Language.ENGLISH;
        } else if (kannadaChars > 0 && latinChars > 0) {
            return Language.MIXED;
        } else {
            return Language.ENGLISH; // Default
        }
    }

    /**
     * Infer expected language from keyboard layout
     */
    public static Language getExpectedLanguage(LayoutDetector.KeyboardLayout layout) {
        switch (layout) {
            case QWERTY:
                return Language.ENGLISH;
            case PHONETIC:
                return Language.MIXED;  // Users may want both
            case STANDARD:
            case CUSTOM:
                return Language.KANNADA;
            default:
                return Language.ENGLISH;
        }
    }
}
```

---

## Enhanced Prediction Engine

### Updated PredictionEngine.java

```java
public class PredictionEngine {
    // Dual dictionaries
    private TrieDict kannadaDict;
    private TrieDict englishDict;

    // Dual n-gram models
    private NgramModel kannadaNgramModel;
    private NgramModel englishNgramModel;

    // Unified user learning (handles both languages)
    private UserLearningModel userModel;

    // Language detection
    private LanguageDetector languageDetector;

    public PredictionEngine(Context context) {
        this.languageDetector = new LanguageDetector();
        this.userModel = new UserLearningModel(context);

        initializeAsync(context);
    }

    private void initializeAsync(Context context) {
        new Thread(() -> {
            try {
                // Load Kannada resources
                kannadaDict = new TrieDict();
                kannadaDict.load(context.getAssets().open("dictionaries/kannada_base.dict"));
                kannadaNgramModel = new NgramModel();
                kannadaNgramModel.load(context.getAssets().open("dictionaries/kannada_bigrams.bin"));

                // Load English resources
                englishDict = new TrieDict();
                englishDict.load(context.getAssets().open("dictionaries/english_base.dict"));
                englishNgramModel = new NgramModel();
                englishNgramModel.load(context.getAssets().open("dictionaries/english_bigrams.bin"));

                isInitialized = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Enhanced prediction with bilingual support
     */
    public List<Suggestion> predict(String currentWord, String previousWord,
                                     LayoutDetector.KeyboardLayout layout) {
        if (!isInitialized || currentWord.isEmpty()) {
            return Collections.emptyList();
        }

        // Detect input language
        Language inputLanguage = languageDetector.detectLanguage(currentWord);
        Language expectedLanguage = languageDetector.getExpectedLanguage(layout);

        // Normalize input
        String normalized = normalizer.normalize(currentWord, layout);

        // Get predictions from appropriate language(s)
        List<Suggestion> suggestions = new ArrayList<>();

        if (layout == LayoutDetector.KeyboardLayout.PHONETIC) {
            // Phonetic layout: Show BOTH Kannada transliterations AND English words
            suggestions.addAll(getKannadaSuggestions(normalized, previousWord));
            suggestions.addAll(getEnglishSuggestions(normalized, previousWord));

        } else if (layout.isKannadaLayout()) {
            // Kannada layouts: Primarily Kannada, with occasional English
            suggestions.addAll(getKannadaSuggestions(normalized, previousWord));

            // Add English if user is typing in Latin script (mixed typing)
            if (inputLanguage == Language.ENGLISH || inputLanguage == Language.MIXED) {
                suggestions.addAll(getEnglishSuggestions(normalized, previousWord));
            }

        } else {
            // QWERTY: Primarily English
            suggestions.addAll(getEnglishSuggestions(normalized, previousWord));
        }

        // Add user-learned words (both languages)
        suggestions.addAll(getUserSuggestions(normalized));

        // Rank and return top suggestions
        return ranker.rank(suggestions, normalized, layout, inputLanguage);
    }

    private List<Suggestion> getKannadaSuggestions(String prefix, String context) {
        List<Suggestion> results = new ArrayList<>();

        // Dictionary completions
        List<String> dictWords = kannadaDict.getCompletions(prefix, 15);
        for (String word : dictWords) {
            results.add(new Suggestion.Builder()
                .word(word)
                .score(5.0)
                .source(Suggestion.Source.DICTIONARY)
                .build());
        }

        // N-gram predictions
        List<String> ngramWords = kannadaNgramModel.predict(context, prefix, 10);
        for (int i = 0; i < ngramWords.size(); i++) {
            results.add(new Suggestion.Builder()
                .word(ngramWords.get(i))
                .score(10.0 - i)
                .source(Suggestion.Source.NGRAM)
                .build());
        }

        return results;
    }

    private List<Suggestion> getEnglishSuggestions(String prefix, String context) {
        List<Suggestion> results = new ArrayList<>();

        // Normalize to lowercase for English
        String lowerPrefix = prefix.toLowerCase(Locale.ENGLISH);

        // Dictionary completions
        List<String> dictWords = englishDict.getCompletions(lowerPrefix, 15);
        for (String word : dictWords) {
            results.add(new Suggestion.Builder()
                .word(word)
                .score(5.0)
                .source(Suggestion.Source.DICTIONARY)
                .build());
        }

        // N-gram predictions
        List<String> ngramWords = englishNgramModel.predict(context, lowerPrefix, 10);
        for (int i = 0; i < ngramWords.size(); i++) {
            results.add(new Suggestion.Builder()
                .word(ngramWords.get(i))
                .score(10.0 - i)
                .source(Suggestion.Source.NGRAM)
                .build());
        }

        return results;
    }

    private List<Suggestion> getUserSuggestions(String prefix) {
        List<String> userWords = userModel.getSuggestions(prefix, 10);
        List<Suggestion> results = new ArrayList<>();

        for (int i = 0; i < userWords.size(); i++) {
            results.add(new Suggestion.Builder()
                .word(userWords.get(i))
                .score(15.0 - i)  // High score for user words
                .source(Suggestion.Source.USER_LEARNED)
                .build());
        }

        return results;
    }
}
```

---

## Enhanced Suggestion Ranking

### SuggestionRanker.java (Bilingual Version)

```java
public class SuggestionRanker {

    /**
     * Rank suggestions with language-aware scoring
     */
    public List<Suggestion> rank(List<Suggestion> candidates, String currentInput,
                                  LayoutDetector.KeyboardLayout layout,
                                  LanguageDetector.Language inputLanguage) {

        // Deduplicate and merge scores
        Map<String, Suggestion> scoreMap = new HashMap<>();
        for (Suggestion s : candidates) {
            Suggestion existing = scoreMap.get(s.getWord());
            if (existing != null) {
                // Merge: boost score if word appears from multiple sources
                existing.adjustScore(s.getScore() * 0.5);
            } else {
                scoreMap.put(s.getWord(), s);
            }
        }

        // Apply layout-specific boosts
        for (Suggestion s : scoreMap.values()) {
            applyLayoutBoost(s, layout, inputLanguage);
            applyEditDistancePenalty(s, currentInput);
        }

        // Sort by score and return top N
        List<Suggestion> ranked = new ArrayList<>(scoreMap.values());
        Collections.sort(ranked);

        // Ensure balanced language mix for phonetic layout
        if (layout == LayoutDetector.KeyboardLayout.PHONETIC) {
            return balanceLanguages(ranked, 5);
        } else {
            return ranked.subList(0, Math.min(5, ranked.size()));
        }
    }

    /**
     * Apply boost based on layout and expected language
     */
    private void applyLayoutBoost(Suggestion s, LayoutDetector.KeyboardLayout layout,
                                   LanguageDetector.Language inputLanguage) {

        if (layout == LayoutDetector.KeyboardLayout.PHONETIC) {
            // Phonetic: Boost Kannada transliterations slightly
            if (s.isKannada()) {
                s.multiplyScore(1.2);
            }

        } else if (layout.isKannadaLayout()) {
            // Kannada layouts: Heavily favor Kannada
            if (s.isKannada()) {
                s.multiplyScore(1.5);
            } else {
                s.multiplyScore(0.5);  // Penalize English
            }

        } else {
            // QWERTY: Favor English
            if (!s.isKannada()) {
                s.multiplyScore(1.3);
            } else {
                s.multiplyScore(0.7);  // Penalize Kannada
            }
        }

        // Boost if suggestion language matches input language
        if ((inputLanguage == LanguageDetector.Language.KANNADA && s.isKannada()) ||
            (inputLanguage == LanguageDetector.Language.ENGLISH && !s.isKannada())) {
            s.multiplyScore(1.1);
        }
    }

    /**
     * Apply edit distance penalty (favor closer matches)
     */
    private void applyEditDistancePenalty(Suggestion s, String input) {
        // For Kannada, compare canonical forms
        String compareInput = input;
        String compareWord = s.getWord();

        if (s.isKannada() && compareWord.startsWith(input)) {
            // Prefix match - no penalty
            return;
        }

        int distance = levenshteinDistance(compareInput, compareWord);
        double penalty = 1.0 / (1.0 + distance * 0.2);
        s.multiplyScore(penalty);
    }

    /**
     * Ensure language balance for phonetic layout (mix Kannada + English)
     */
    private List<Suggestion> balanceLanguages(List<Suggestion> suggestions, int maxResults) {
        List<Suggestion> result = new ArrayList<>();
        List<Suggestion> kannada = new ArrayList<>();
        List<Suggestion> english = new ArrayList<>();

        for (Suggestion s : suggestions) {
            if (s.isKannada()) {
                kannada.add(s);
            } else {
                english.add(s);
            }
        }

        // Strategy: 3 Kannada + 2 English (or 3 English + 2 Kannada based on scores)
        int kannadaCount = Math.min(3, kannada.size());
        int englishCount = Math.min(2, english.size());

        // Adjust if one language has very high scores
        if (!kannada.isEmpty() && !english.isEmpty()) {
            double topKannadaScore = kannada.get(0).getScore();
            double topEnglishScore = english.get(0).getScore();

            if (topEnglishScore > topKannadaScore * 1.5) {
                // English suggestions much better - show more English
                englishCount = 3;
                kannadaCount = 2;
            }
        }

        // Add top N from each
        result.addAll(kannada.subList(0, Math.min(kannadaCount, kannada.size())));
        result.addAll(english.subList(0, Math.min(englishCount, english.size())));

        // Re-sort combined list
        Collections.sort(result);

        return result.subList(0, Math.min(maxResults, result.size()));
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,      // deletion
                        dp[i][j - 1] + 1),     // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    );
                }
            }
        }

        return dp[a.length()][b.length()];
    }
}
```

---

## English Dictionary Resources

### 1. Build English Dictionary

**Option A: Use Android's Built-in Dictionary**

Android includes English dictionaries at:
```
/system/usr/share/dict/en_US.txt
/system/usr/share/dict/en_GB.txt
```

Extract words programmatically:
```java
// DictionaryExtractor.java
public class DictionaryExtractor {
    public static Set<String> extractAndroidDictionary(Context context) {
        Set<String> words = new HashSet<>();

        try {
            // Use Android's spell checker service
            TextServicesManager tsm = (TextServicesManager)
                context.getSystemService(Context.TEXT_SERVICES_SERVICE);

            SpellCheckerSession session = tsm.newSpellCheckerSession(
                null, Locale.US, null, false);

            // Alternative: Read from system dictionary files
            // This requires root access on most devices
        } catch (Exception e) {
            e.printStackTrace();
        }

        return words;
    }
}
```

**Option B: Use Open Source English Wordlists**

1. **SCOWL (Spell Checker Oriented Word Lists)**
   ```bash
   wget http://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz
   tar -xzf scowl-2020.12.07.tar.gz

   # Extract American English words (size 60 = ~200k words)
   cd scowl-2020.12.07/final
   cat english-words.60 > english_base.txt
   ```

2. **Google's 10,000 Most Common Words**
   ```bash
   wget https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-usa.txt
   ```

3. **Mozilla's Hunspell Dictionary**
   ```bash
   git clone https://github.com/mozilla-b2g/gaia
   # Extract from gaia/apps/keyboard/js/imes/latin/dictionaries/en_us.dict
   ```

**Option C: Frequency-Ordered Dictionary (Recommended)**

Use frequency-ordered word list for better predictions:
```bash
# Download from Peter Norvig's corpus
wget http://norvig.com/ngrams/count_1w.txt

# Process to extract top 200k words
head -200000 count_1w.txt | cut -f2 > english_frequency.txt
```

### 2. Pre-process English Dictionary

```python
# build_english_dict.py
import json

def build_frequency_dict():
    """Build English dictionary with frequencies from count_1w.txt"""
    words = []

    with open('count_1w.txt', 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) == 2:
                word = parts[0].lower()
                frequency = int(parts[1])

                # Filter: only alphabetic, length 2-20
                if word.isalpha() and 2 <= len(word) <= 20:
                    words.append((word, frequency))

    # Save as tab-separated file
    with open('english_frequency.txt', 'w', encoding='utf-8') as f:
        for word, freq in words[:200000]:  # Top 200k
            f.write(f'{word}\t{freq}\n')

    print(f'Built dictionary with {len(words)} words')

if __name__ == '__main__':
    build_frequency_dict()
```

### 3. Build English Binary Dictionary

```java
// In tools/DictionaryBuilder.java

public static void buildEnglishDict() {
    List<Entry> words = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(
            new FileReader("english_frequency.txt"))) {

        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
            if (parts.length == 2) {
                String word = parts[0];
                int frequency = Integer.parseInt(parts[1]);
                words.add(new Entry(word, frequency));
            }
        }

    } catch (IOException e) {
        e.printStackTrace();
    }

    // Build binary trie format
    buildBinaryDict(words, "app/src/main/assets/dictionaries/english_base.dict");
}
```

---

## English N-gram Model

### Build English Bigrams

```python
# build_english_bigrams.py
from collections import defaultdict, Counter
import pickle

def extract_bigrams(corpus_file):
    """Extract bigrams from English text corpus"""
    bigrams = defaultdict(Counter)

    with open(corpus_file, 'r', encoding='utf-8') as f:
        for line in f:
            words = line.lower().strip().split()

            # Extract bigrams
            for i in range(len(words) - 1):
                word1 = words[i]
                word2 = words[i + 1]

                # Filter: only alphabetic words
                if word1.isalpha() and word2.isalpha():
                    bigrams[word1][word2] += 1

    return bigrams

def save_bigrams(bigrams, output_file):
    """Save bigrams in binary format"""
    # Convert to simple dict for serialization
    data = {}
    for word1, counter in bigrams.items():
        data[word1] = dict(counter.most_common(20))  # Top 20 per word

    with open(output_file, 'wb') as f:
        pickle.dump(data, f)

# Use English Wikipedia or Brown corpus
corpus_file = 'english_corpus.txt'  # Download separately
bigrams = extract_bigrams(corpus_file)
save_bigrams(bigrams, 'english_bigrams.bin')
```

**Corpus Sources**:
- **Brown Corpus**: http://www.nltk.org/nltk_data/
- **Wikipedia**: https://dumps.wikimedia.org/enwiki/
- **Common Crawl**: https://commoncrawl.org/

---

## Updated File Structure

```
app/src/main/assets/dictionaries/
├── kannada_base.dict           # Kannada dictionary (50k words)
├── kannada_frequency.txt       # Kannada word frequencies
├── kannada_bigrams.bin         # Kannada n-grams
├── english_base.dict           # ✨ English dictionary (200k words)
├── english_frequency.txt       # ✨ English word frequencies
└── english_bigrams.bin         # ✨ English n-grams

app/src/main/java/.../prediction/
├── LanguageDetector.java       # ✨ NEW: Detect English vs Kannada
├── PredictionEngine.java       # ✨ UPDATED: Dual-language support
├── SuggestionRanker.java       # ✨ UPDATED: Language-aware ranking
├── LayoutDetector.java         # ✅ Existing
└── Suggestion.java             # ✅ Existing
```

---

## Usage Examples

### Example 1: QWERTY Layout (English)

```
Input: "hel"
Layout: QWERTY
Expected Language: English

Predictions:
1. hello      (dict: 9.5, ngram: 8.0) → Score: 17.5
2. help       (dict: 9.0, user: 5.0)  → Score: 14.0
3. held       (dict: 7.0)              → Score: 7.0
4. helmet     (dict: 6.5)              → Score: 6.5
5. helpful    (dict: 6.0)              → Score: 6.0
```

### Example 2: Phonetic Layout (Bilingual)

```
Input: "ka"
Layout: Phonetic
Expected Language: Mixed

Predictions:
1. ಕನ್ನಡ      (user: 15, ngram: 9)   → Score: 24.0  [Kannada]
2. ಕರ್ನಾಟಕ   (dict: 8, ngram: 7)    → Score: 15.0  [Kannada]
3. car        (dict: 9, ngram: 8)    → Score: 17.0  [English]
4. can        (dict: 9, ngram: 7)    → Score: 16.0  [English]
5. call       (dict: 8, ngram: 6)    → Score: 14.0  [English]

Balanced Output: [ಕನ್ನಡ, car, ಕರ್ನಾಟಕ, can, call]
```

### Example 3: Standard Kannada Layout

```
Input: "ಕ"
Layout: Standard Kannada
Expected Language: Kannada

Predictions:
1. ಕನ್ನಡ      (user: 15, ngram: 9)   → Score: 24.0
2. ಕರ್ನಾಟಕ   (dict: 8, ngram: 7)    → Score: 15.0
3. ಕಾರು       (dict: 7)              → Score: 7.0
4. ಕಾಲ        (dict: 6.5)            → Score: 6.5
5. ಕೆಲಸ       (dict: 6.0)            → Score: 6.0
```

---

## Performance Considerations

### Memory Usage (Updated)

- Kannada Dictionary: ~2 MB
- **English Dictionary: ~8 MB** (200k words)
- Kannada Bigrams: ~3 MB
- **English Bigrams: ~10 MB**
- User Database: ~2 MB
- Runtime Overhead: ~5 MB

**Total: ~30 MB** (within target)

### Optimization Strategies

1. **Lazy Loading**:
   ```java
   // Load English dict only when needed
   if (layout == QWERTY && englishDict == null) {
       englishDict = new TrieDict();
       englishDict.load(...);
   }
   ```

2. **Dictionary Compression**:
   - Use GZIP compression for binary files
   - Decompress on-the-fly during loading
   - Saves ~40% storage

3. **Tiered Dictionary**:
   - Core: 50k most common English words (loaded always)
   - Extended: 150k additional words (lazy load)

---

## Updated Implementation Checklist

### Phase 1: Foundation (Week 1-2)
- [x] LayoutDetector.java
- [x] Suggestion.java
- [ ] LanguageDetector.java ✨ NEW
- [ ] InputNormalizer.java
- [ ] UserDatabaseHelper.java

### Phase 2: English Resources (Week 3)
- [ ] Download English wordlist (200k) ✨
- [ ] Build english_base.dict ✨
- [ ] Extract English bigrams from corpus ✨
- [ ] Test English predictions on QWERTY

### Phase 3: Kannada Resources (Week 4)
- [ ] Download Kannada wordlist (50k)
- [ ] Build kannada_base.dict
- [ ] Extract Kannada bigrams
- [ ] Test Kannada predictions

### Phase 4: Bilingual Engine (Week 5)
- [ ] Update PredictionEngine.java for dual-language ✨
- [ ] Update SuggestionRanker.java for language mixing ✨
- [ ] Implement balanced suggestion strategy
- [ ] Test phonetic layout (mixed predictions)

### Phase 5: Integration & Testing (Week 6-7)
- [ ] Integrate with InputLogic.java
- [ ] Test all layouts (QWERTY, Phonetic, Standard, Custom)
- [ ] Performance profiling
- [ ] Memory optimization

---

## Quick Start: Build English Dictionary

```bash
# Step 1: Download frequency list
wget http://norvig.com/ngrams/count_1w.txt

# Step 2: Process
python3 build_english_dict.py

# Step 3: Build binary format
javac tools/DictionaryBuilder.java
java tools.DictionaryBuilder english

# Step 4: Copy to assets
cp english_base.dict app/src/main/assets/dictionaries/
```

---

## Testing Strategy (Updated)

```java
// BilingualPredictionTest.java

@Test
public void testEnglishPrediction_QWERTY() {
    List<Suggestion> results = engine.predict("hel", "", QWERTY);

    assertTrue(results.size() > 0);
    assertEquals("hello", results.get(0).getWord());
    assertFalse(results.get(0).isKannada());
}

@Test
public void testKannadaPrediction_StandardLayout() {
    List<Suggestion> results = engine.predict("ಕ", "", STANDARD);

    assertTrue(results.size() > 0);
    assertTrue(results.get(0).getWord().startsWith("ಕ"));
    assertTrue(results.get(0).isKannada());
}

@Test
public void testBilingualPrediction_PhoneticLayout() {
    List<Suggestion> results = engine.predict("ka", "", PHONETIC);

    // Should have both Kannada and English
    boolean hasKannada = results.stream().anyMatch(Suggestion::isKannada);
    boolean hasEnglish = results.stream().anyMatch(s -> !s.isKannada());

    assertTrue(hasKannada);
    assertTrue(hasEnglish);
}
```

---

## Next Steps

1. ✅ **Review bilingual design**
2. ⬜ **Download English resources** (count_1w.txt)
3. ⬜ **Implement LanguageDetector.java**
4. ⬜ **Build English dictionary** (200k words)
5. ⬜ **Update PredictionEngine** for dual-language
6. ⬜ **Test on all layouts**

The system now provides **full English prediction** alongside Kannada with intelligent language detection and context-aware mixing!
