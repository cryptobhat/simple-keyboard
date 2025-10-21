# Kannada IME - Universal Prediction Engine Design

## Architecture Components

### 1. Layout Detection & Input Normalization

**Purpose**: Detect active keyboard layout and normalize all inputs to canonical Unicode

**Components**:
```java
// LayoutDetector.java
public class LayoutDetector {
    public enum KeyboardLayout {
        PHONETIC,      // English → Kannada transliteration
        INSCRIPT,      // Standard Inscript layout
        BARAHA,        // Baraha phonetic scheme
        AKSHARA,       // Akshara layout
        QWERTY         // English QWERTY
    }

    // Detect current layout from subtype
    public KeyboardLayout detectLayout(Subtype subtype);

    // Map raw keycode to Unicode based on layout
    public String mapToUnicode(int keyCode, KeyboardLayout layout);
}

// InputNormalizer.java
public class InputNormalizer {
    // Convert layout-specific input to canonical Unicode
    public String normalize(String input, KeyboardLayout layout);

    // Handle half-consonants, conjuncts, viramas
    public String canonicalizeKannada(String text);
}
```

**Layout Mapping Tables**:
- Each layout needs a keycode → Unicode mapping
- Phonetic: Uses existing KannadaTransliterator
- Inscript/Baraha/Akshara: Static key mapping tables

---

### 2. Core Prediction Engine

**Purpose**: Multi-layered prediction using n-grams, frequency, and context

```java
// PredictionEngine.java
public class PredictionEngine {
    private TrieDict dictionary;
    private NgramModel ngramModel;
    private UserLearningModel userModel;
    private FrequencyTable frequencyTable;

    // Main prediction method
    public List<Suggestion> predict(
        String currentWord,
        String previousWord,
        KeyboardLayout layout
    ) {
        // 1. Normalize input
        String normalized = inputNormalizer.normalize(currentWord, layout);

        // 2. Get candidate completions from Trie
        List<String> candidates = dictionary.getCompletions(normalized);

        // 3. Get n-gram predictions
        List<String> ngramPredictions = ngramModel.predict(previousWord, normalized);

        // 4. Get user-learned words
        List<String> userWords = userModel.getSuggestions(normalized);

        // 5. Combine and rank
        return rankSuggestions(candidates, ngramPredictions, userWords, normalized);
    }

    // Learn from user input
    public void learn(String word, String context) {
        userModel.addWord(word, context);
        ngramModel.updateBigram(context, word);
        frequencyTable.incrementFrequency(word);
    }
}
```

---

### 3. Data Structures

#### 3.1 Trie Dictionary (Fast Prefix Search)

```java
// TrieDict.java
public class TrieDict {
    private TrieNode root;

    static class TrieNode {
        Map<Character, TrieNode> children;
        boolean isWordEnd;
        int frequency;
        long lastUsed; // timestamp for recency
    }

    // Insert word with frequency
    public void insert(String word, int frequency) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
        }
        node.isWordEnd = true;
        node.frequency = frequency;
    }

    // Get all completions for prefix
    public List<String> getCompletions(String prefix, int maxResults) {
        TrieNode node = findNode(prefix);
        if (node == null) return Collections.emptyList();

        List<String> results = new ArrayList<>();
        collectWords(node, prefix, results, maxResults);
        return results;
    }

    // Persist to disk (SQLite or binary format)
    public void save(String filepath);
    public void load(String filepath);
}
```

#### 3.2 N-gram Model (Context-Aware Predictions)

```java
// NgramModel.java
public class NgramModel {
    // Bigram: previous_word → current_word → count
    private Map<String, Map<String, Integer>> bigrams;

    // Trigram: (word1, word2) → word3 → count
    private Map<Pair<String, String>, Map<String, Integer>> trigrams;

    // Add bigram
    public void updateBigram(String word1, String word2) {
        bigrams.computeIfAbsent(word1, k -> new HashMap<>())
                .merge(word2, 1, Integer::sum);
    }

    // Predict next word based on previous context
    public List<String> predict(String previousWord, String currentPrefix) {
        Map<String, Integer> candidates = bigrams.get(previousWord);
        if (candidates == null) return Collections.emptyList();

        // Filter by prefix and sort by frequency
        return candidates.entrySet().stream()
            .filter(e -> e.getKey().startsWith(currentPrefix))
            .sorted((a, b) -> b.getValue() - a.getValue())
            .map(Map.Entry::getKey)
            .limit(10)
            .collect(Collectors.toList());
    }

    // Persist to disk
    public void save(String filepath);
    public void load(String filepath);
}
```

#### 3.3 User Learning Model

```java
// UserLearningModel.java
public class UserLearningModel {
    private SQLiteDatabase db;

    // Schema: user_words(word TEXT, frequency INT, last_used TIMESTAMP, context TEXT)

    public void addWord(String word, String context) {
        ContentValues values = new ContentValues();
        values.put("word", word);
        values.put("context", context);
        values.put("last_used", System.currentTimeMillis());

        // Insert or increment frequency
        db.execSQL("INSERT INTO user_words (word, frequency, last_used, context) " +
                   "VALUES (?, 1, ?, ?) " +
                   "ON CONFLICT(word) DO UPDATE SET " +
                   "frequency = frequency + 1, last_used = ?",
                   new Object[]{word, System.currentTimeMillis(), context,
                               System.currentTimeMillis()});
    }

    public List<String> getSuggestions(String prefix) {
        Cursor cursor = db.rawQuery(
            "SELECT word FROM user_words " +
            "WHERE word LIKE ? " +
            "ORDER BY frequency DESC, last_used DESC " +
            "LIMIT 10",
            new String[]{prefix + "%"}
        );

        List<String> results = new ArrayList<>();
        while (cursor.moveToNext()) {
            results.add(cursor.getString(0));
        }
        cursor.close();
        return results;
    }

    // Clear old entries (privacy/storage management)
    public void pruneOldEntries(long olderThanTimestamp) {
        db.delete("user_words", "last_used < ? AND frequency < 3",
                  new String[]{String.valueOf(olderThanTimestamp)});
    }
}
```

---

### 4. Suggestion Ranking System

```java
// SuggestionRanker.java
public class SuggestionRanker {

    public static class Suggestion {
        String word;
        double score;
        SuggestionSource source;

        enum SuggestionSource {
            DICTIONARY,
            USER_LEARNED,
            NGRAM,
            FREQUENCY
        }
    }

    public List<Suggestion> rankSuggestions(
        List<String> dictCandidates,
        List<String> ngramPredictions,
        List<String> userWords,
        String currentInput
    ) {
        Map<String, Suggestion> scoreMap = new HashMap<>();

        // Score dictionary candidates (base score)
        for (String word : dictCandidates) {
            scoreMap.put(word, new Suggestion(word, 1.0, DICTIONARY));
        }

        // Boost n-gram predictions (context bonus)
        for (int i = 0; i < ngramPredictions.size(); i++) {
            String word = ngramPredictions.get(i);
            Suggestion s = scoreMap.getOrDefault(word, new Suggestion(word, 0, NGRAM));
            s.score += (10.0 - i) * 0.5; // Higher rank = higher score
            scoreMap.put(word, s);
        }

        // Boost user-learned words (personalization bonus)
        for (int i = 0; i < userWords.size(); i++) {
            String word = userWords.get(i);
            Suggestion s = scoreMap.getOrDefault(word, new Suggestion(word, 0, USER_LEARNED));
            s.score += (10.0 - i) * 0.8; // User words get higher weight
            scoreMap.put(word, s);
        }

        // Apply edit distance penalty for partial matches
        for (Suggestion s : scoreMap.values()) {
            int distance = levenshteinDistance(currentInput, s.word);
            s.score *= (1.0 / (1.0 + distance * 0.3));
        }

        // Sort by score and return top results
        return scoreMap.values().stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(5)
            .collect(Collectors.toList());
    }

    private int levenshteinDistance(String a, String b) {
        // Standard edit distance algorithm
        // ...
    }
}
```

---

### 5. Integration with InputLogic

```java
// In InputLogic.java

public class InputLogic {
    private PredictionEngine predictionEngine;
    private LayoutDetector layoutDetector;
    private StringBuilder currentWord;
    private String previousWord;

    public void onKeyPressed(int keyCode, Event event) {
        // Detect current layout
        KeyboardLayout layout = layoutDetector.detectLayout(
            mLatinIME.getCurrentSubtype()
        );

        if (isLetterKey(keyCode)) {
            // Map key to Unicode
            String unicodeChar = layoutDetector.mapToUnicode(keyCode, layout);
            currentWord.append(unicodeChar);

            // Get predictions
            List<Suggestion> suggestions = predictionEngine.predict(
                currentWord.toString(),
                previousWord,
                layout
            );

            // Update suggestion strip
            updateSuggestionStrip(suggestions);
        }

        if (keyCode == KEY_SPACE || keyCode == KEY_ENTER) {
            // User accepted current word
            String finalWord = currentWord.toString();
            predictionEngine.learn(finalWord, previousWord);

            previousWord = finalWord;
            currentWord.setLength(0);
        }
    }

    public void onSuggestionPicked(Suggestion suggestion) {
        // User selected a suggestion
        mConnection.commitText(suggestion.word, 1);
        predictionEngine.learn(suggestion.word, previousWord);

        previousWord = suggestion.word;
        currentWord.setLength(0);
    }
}
```

---

## Data Files Structure

### Directory Layout
```
app/src/main/assets/
├── dictionaries/
│   ├── kannada_base.dict         # Base Kannada dictionary (50k words)
│   ├── kannada_frequency.txt     # Word frequency list
│   ├── english_base.dict         # English dictionary (20k words)
│   └── bigrams.bin               # Pre-trained bigram model
│
└── layouts/
    ├── inscript_mapping.json
    ├── baraha_mapping.json
    └── akshara_mapping.json

app/data/data/rkr.simplekeyboard/databases/
└── user_learned.db               # SQLite user learning database
```

### Dictionary Format

**Kannada Base Dictionary** (`kannada_base.dict`):
```
Binary Trie format for fast loading:
- Magic header: "KNDICT01"
- Word count: 4 bytes
- Trie nodes: compressed binary format
- Each word: UTF-8 bytes + frequency (4 bytes)
```

**Frequency List** (`kannada_frequency.txt`):
```
ಕನ್ನಡ	50000
ನಮಸ್ಕಾರ	45000
ಧನ್ಯವಾದ	40000
...
```

**Layout Mapping** (`inscript_mapping.json`):
```json
{
  "layout_name": "inscript",
  "version": "1.0",
  "mappings": {
    "q": "ೌ",
    "w": "ೈ",
    "e": "ಾ",
    "r": "ೀ",
    "t": "ೂ",
    ...
  },
  "shift_mappings": {
    "q": "ಔ",
    "w": "ಐ",
    ...
  }
}
```

---

## Performance Optimizations

### 1. Lazy Loading
```java
public class PredictionEngine {
    private volatile boolean isInitialized = false;

    public void initializeAsync(Context context) {
        new Thread(() -> {
            // Load dictionaries in background
            dictionary.load(context.getAssets().open("kannada_base.dict"));
            ngramModel.load(context.getAssets().open("bigrams.bin"));

            isInitialized = true;
        }).start();
    }
}
```

### 2. Caching
```java
private LruCache<String, List<Suggestion>> predictionCache =
    new LruCache<>(1000); // Cache last 1000 predictions

public List<Suggestion> predict(String word, String context, KeyboardLayout layout) {
    String cacheKey = word + "|" + context + "|" + layout.name();

    List<Suggestion> cached = predictionCache.get(cacheKey);
    if (cached != null) return cached;

    List<Suggestion> result = computePredictions(word, context, layout);
    predictionCache.put(cacheKey, result);
    return result;
}
```

### 3. Memory Management
```java
// Limit user database size
public void pruneUserData() {
    long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
    userModel.pruneOldEntries(thirtyDaysAgo);

    // Keep only top 10,000 user words
    db.execSQL("DELETE FROM user_words WHERE word NOT IN " +
               "(SELECT word FROM user_words ORDER BY frequency DESC LIMIT 10000)");
}
```

---

## Privacy & Data Management

### 1. User Control
```java
public class PredictionSettings {
    // User can disable learning
    public void setLearningEnabled(boolean enabled);

    // Clear all learned data
    public void clearUserData() {
        userModel.clear();
        ngramModel.resetUserBigrams();
        predictionCache.evictAll();
    }

    // Export user dictionary (for backup/transfer)
    public File exportUserDictionary();
    public void importUserDictionary(File file);
}
```

### 2. Data Encryption (Optional)
```java
// Encrypt user database using Android Keystore
public class SecureUserModel extends UserLearningModel {
    private SecretKey encryptionKey;

    @Override
    public void addWord(String word, String context) {
        String encryptedWord = encrypt(word);
        super.addWord(encryptedWord, context);
    }
}
```

---

## Testing Strategy

### Unit Tests
```java
@Test
public void testLayoutDetection() {
    Subtype phoneticSubtype = createSubtype("kannada_phonetic");
    assertEquals(KeyboardLayout.PHONETIC,
                 layoutDetector.detectLayout(phoneticSubtype));
}

@Test
public void testPredictionRanking() {
    List<String> dict = Arrays.asList("ಕನ್ನಡ", "ಕರ್ನಾಟಕ");
    List<String> ngram = Arrays.asList("ಕನ್ನಡ");
    List<String> user = Arrays.asList("ಕನ್ನಡ");

    List<Suggestion> ranked = ranker.rankSuggestions(dict, ngram, user, "ಕ");

    assertEquals("ಕನ್ನಡ", ranked.get(0).word); // Should be top due to n-gram + user boost
}
```

### Integration Tests
```java
@Test
public void testCrossLayoutPrediction() {
    // Type "ka" in phonetic layout
    List<Suggestion> phoneticResults = engine.predict("ka", "", PHONETIC);

    // Type equivalent in Inscript
    List<Suggestion> inscriptResults = engine.predict("ಕ", "", INSCRIPT);

    // Should produce similar suggestions (both are ಕ)
    assertTrue(phoneticResults.get(0).word.equals(inscriptResults.get(0).word));
}
```

---

## Implementation Timeline

### Phase 1 (Week 1-2): Foundation
- [ ] Implement LayoutDetector with all 5 layout mappings
- [ ] Create InputNormalizer for Unicode canonicalization
- [ ] Build TrieDict with basic dictionary loading
- [ ] Set up SQLite UserLearningModel

### Phase 2 (Week 3-4): Core Engine
- [ ] Implement NgramModel with bigram support
- [ ] Build SuggestionRanker with scoring algorithm
- [ ] Integrate PredictionEngine with InputLogic
- [ ] Add suggestion strip UI

### Phase 3 (Week 5-6): Data & Optimization
- [ ] Compile Kannada base dictionary (50k words)
- [ ] Generate frequency lists from corpus
- [ ] Train initial bigram model
- [ ] Implement caching and lazy loading

### Phase 4 (Week 7-8): Polish & Testing
- [ ] Add user settings for learning control
- [ ] Implement data export/import
- [ ] Performance profiling and optimization
- [ ] Comprehensive testing across all layouts

---

## Open Source Resources

### Kannada Dictionaries
- **TDIL Kannada Dictionary**: https://github.com/mozilla-b2g/gaia/tree/master/apps/keyboard/js/imes/kannada
- **Kannada Wikipedia Corpus**: Extract word frequencies
- **ILCI Kannada Corpus**: http://tdil-dc.in/index.php?lang=en

### N-gram Models
- **Indian Language N-grams**: http://www.cfilt.iitb.ac.in/
- **Kannada Language Model**: Train from Wikipedia dumps

### Layout References
- **Inscript**: Official GOI standard
- **Baraha**: http://www.baraha.com/help/Keyboards.htm
- **Akshara**: Community-contributed layout

---

## File List to Create

1. **Core Engine**:
   - `PredictionEngine.java`
   - `LayoutDetector.java`
   - `InputNormalizer.java`
   - `SuggestionRanker.java`

2. **Data Structures**:
   - `TrieDict.java`
   - `NgramModel.java`
   - `UserLearningModel.java`
   - `FrequencyTable.java`

3. **Utilities**:
   - `DictionaryBuilder.java` (build-time tool)
   - `CorpusProcessor.java` (extract frequencies)
   - `LayoutMapper.java`

4. **UI Components**:
   - `SuggestionStripView.java` (already exists, enhance)
   - `PredictionSettings.java`

5. **Data Files**:
   - `kannada_base.dict`
   - `kannada_frequency.txt`
   - `english_base.dict`
   - `bigrams.bin`
   - Layout JSON files

---

## Success Metrics

- **Accuracy**: Top-3 suggestion accuracy > 85%
- **Performance**: Predictions generated < 50ms
- **Memory**: Total RAM usage < 30MB
- **Storage**: Base dictionaries < 5MB, user data < 2MB
- **Learning**: Personalized accuracy improves 10%+ after 100 typed words
