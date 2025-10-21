# Kannada Prediction Engine - Implementation Roadmap

## Quick Start Guide

This document provides a step-by-step implementation guide for building the universal prediction engine.

---

## Phase 1: Foundation (Week 1-2)

### Step 1.1: Create Package Structure

```
app/src/main/java/rkr/simplekeyboard/inputmethod/latin/
└── prediction/
    ├── LayoutDetector.java          ✅ CREATED
    ├── Suggestion.java               ✅ CREATED
    ├── InputNormalizer.java          ⬜ TODO
    ├── PredictionEngine.java         ⬜ TODO
    ├── SuggestionRanker.java         ⬜ TODO
    ├── dictionary/
    │   ├── TrieDict.java             ⬜ TODO
    │   ├── FrequencyTable.java       ⬜ TODO
    │   └── DictionaryLoader.java     ⬜ TODO
    ├── ngram/
    │   ├── NgramModel.java           ⬜ TODO
    │   └── BigramStore.java          ⬜ TODO
    └── user/
        ├── UserLearningModel.java    ⬜ TODO
        └── UserDatabaseHelper.java   ⬜ TODO
```

### Step 1.2: Implement InputNormalizer

**File**: `prediction/InputNormalizer.java`

```java
public class InputNormalizer {
    // Normalize different layout inputs to canonical Unicode
    public String normalize(String input, LayoutDetector.KeyboardLayout layout) {
        switch (layout) {
            case PHONETIC:
                // Already handled by KannadaTransliterator
                return KannadaTransliterator.transliterate(input);
            case STANDARD:
            case CUSTOM:
                // Direct Kannada input, just clean up combining marks
                return canonicalizeKannada(input);
            case QWERTY:
                // English input, lowercase normalization
                return input.toLowerCase(Locale.ENGLISH);
            default:
                return input;
        }
    }

    // Normalize Kannada Unicode (handle different virama placements, etc.)
    private String canonicalizeKannada(String text) {
        // TODO: Implement Unicode normalization
        // - Convert to NFC (canonical composition)
        // - Handle variant forms of same character
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
    }
}
```

### Step 1.3: Create SQLite User Model

**File**: `prediction/user/UserDatabaseHelper.java`

```java
public class UserDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "user_learned.db";
    private static final int DATABASE_VERSION = 1;

    // Table: user_words
    private static final String CREATE_TABLE_USER_WORDS =
        "CREATE TABLE user_words (" +
        "  word TEXT PRIMARY KEY," +
        "  frequency INTEGER DEFAULT 1," +
        "  last_used INTEGER," +  // timestamp
        "  context TEXT" +         // previous word for bigram learning
        ")";

    // Index for fast prefix search
    private static final String CREATE_INDEX_PREFIX =
        "CREATE INDEX idx_word_prefix ON user_words(word COLLATE NOCASE)";

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER_WORDS);
        db.execSQL(CREATE_INDEX_PREFIX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle future schema migrations
        db.execSQL("DROP TABLE IF EXISTS user_words");
        onCreate(db);
    }
}
```

---

## Phase 2: Core Dictionary (Week 3)

### Step 2.1: Build Kannada Dictionary

**Option A: Quick Start (Use Existing Resources)**

1. Download Kannada wordlist:
   ```bash
   # From Mozilla/Gaia Kannada keyboard
   wget https://raw.githubusercontent.com/mozilla-b2g/gaia/master/apps/keyboard/js/imes/kannada/dict.js
   ```

2. Convert to simple text format:
   ```python
   # dict_converter.py
   import json

   with open('dict.js', 'r', encoding='utf-8') as f:
       data = f.read()
       # Extract word list from JS file
       words = json.loads(data.split('=')[1].strip())

   with open('kannada_words.txt', 'w', encoding='utf-8') as f:
       for word in sorted(set(words)):
           f.write(word + '\n')
   ```

**Option B: Build from Scratch**

1. Extract from Kannada Wikipedia:
   ```bash
   # Download latest Kannada Wikipedia dump
   wget https://dumps.wikimedia.org/knwiki/latest/knwiki-latest-pages-articles.xml.bz2

   # Use WikiExtractor
   git clone https://github.com/attardi/wikiextractor.git
   python wikiextractor/WikiExtractor.py knwiki-latest-pages-articles.xml.bz2
   ```

2. Process corpus to extract words:
   ```python
   # corpus_processor.py
   import re
   from collections import Counter

   def extract_kannada_words(text):
       # Match Kannada words (Unicode range 0C80-0CFF)
       pattern = r'[\u0C80-\u0CFF]+'
       return re.findall(pattern, text)

   word_counter = Counter()

   # Process all extracted Wikipedia articles
   for file in wiki_files:
       with open(file, 'r', encoding='utf-8') as f:
           words = extract_kannada_words(f.read())
           word_counter.update(words)

   # Save top 50,000 words with frequencies
   with open('kannada_frequency.txt', 'w', encoding='utf-8') as f:
       for word, freq in word_counter.most_common(50000):
           f.write(f'{word}\t{freq}\n')
   ```

### Step 2.2: Create Binary Dictionary Format

**File**: `tools/DictionaryBuilder.java` (build-time tool)

```java
public class DictionaryBuilder {
    public static void buildBinaryDict(String inputFile, String outputFile) {
        List<Entry> words = loadWords(inputFile);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputFile)))) {

            // Write header
            out.writeBytes("KNDICT01");  // Magic number
            out.writeInt(words.size());  // Word count

            // Write trie structure
            TrieNode root = buildTrie(words);
            serializeTrie(root, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Entry {
        String word;
        int frequency;
    }

    // ... implementation details
}
```

### Step 2.3: Implement Trie Dictionary

**File**: `prediction/dictionary/TrieDict.java`

```java
public class TrieDict {
    private TrieNode root;

    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isWordEnd = false;
        int frequency = 0;
    }

    // Load from binary file
    public void load(InputStream inputStream) throws IOException {
        DataInputStream in = new DataInputStream(inputStream);

        // Verify header
        byte[] magic = new byte[8];
        in.readFully(magic);
        if (!Arrays.equals(magic, "KNDICT01".getBytes())) {
            throw new IOException("Invalid dictionary format");
        }

        int wordCount = in.readInt();
        root = deserializeTrie(in);
    }

    // Get completions for a prefix
    public List<String> getCompletions(String prefix, int maxResults) {
        TrieNode node = findNode(prefix);
        if (node == null) {
            return Collections.emptyList();
        }

        List<WordFreq> results = new ArrayList<>();
        collectWords(node, prefix, results, maxResults * 2);

        // Sort by frequency and return top N
        Collections.sort(results, (a, b) -> b.frequency - a.frequency);
        return results.stream()
            .limit(maxResults)
            .map(wf -> wf.word)
            .collect(Collectors.toList());
    }

    private TrieNode findNode(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return null;
        }
        return node;
    }

    private void collectWords(TrieNode node, String prefix,
                               List<WordFreq> results, int limit) {
        if (results.size() >= limit) return;

        if (node.isWordEnd) {
            results.add(new WordFreq(prefix, node.frequency));
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectWords(entry.getValue(), prefix + entry.getKey(), results, limit);
        }
    }

    static class WordFreq {
        String word;
        int frequency;
        WordFreq(String w, int f) { word = w; frequency = f; }
    }
}
```

---

## Phase 3: Prediction Engine (Week 4)

### Step 3.1: Core Prediction Engine

**File**: `prediction/PredictionEngine.java`

```java
public class PredictionEngine {
    private TrieDict kannadaDict;
    private TrieDict englishDict;
    private NgramModel ngramModel;
    private UserLearningModel userModel;
    private InputNormalizer normalizer;
    private SuggestionRanker ranker;

    private volatile boolean isInitialized = false;

    public PredictionEngine(Context context) {
        this.normalizer = new InputNormalizer();
        this.ranker = new SuggestionRanker();
        this.userModel = new UserLearningModel(context);

        // Initialize asynchronously
        initializeAsync(context);
    }

    private void initializeAsync(Context context) {
        new Thread(() -> {
            try {
                kannadaDict = new TrieDict();
                kannadaDict.load(context.getAssets().open("dictionaries/kannada_base.dict"));

                englishDict = new TrieDict();
                englishDict.load(context.getAssets().open("dictionaries/english_base.dict"));

                ngramModel = new NgramModel();
                ngramModel.load(context.getAssets().open("dictionaries/bigrams.bin"));

                isInitialized = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Main prediction method
     */
    public List<Suggestion> predict(String currentWord, String previousWord,
                                     LayoutDetector.KeyboardLayout layout) {
        if (!isInitialized || currentWord.isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize input based on layout
        String normalized = normalizer.normalize(currentWord, layout);

        // Get candidates from multiple sources
        List<String> dictCandidates = getDictionaryCandidates(normalized, layout);
        List<String> ngramPredictions = ngramModel.predict(previousWord, normalized, 10);
        List<String> userWords = userModel.getSuggestions(normalized, 10);

        // Rank and return top suggestions
        return ranker.rank(dictCandidates, ngramPredictions, userWords, normalized);
    }

    private List<String> getDictionaryCandidates(String prefix,
                                                  LayoutDetector.KeyboardLayout layout) {
        if (layout.isKannadaLayout()) {
            return kannadaDict.getCompletions(prefix, 20);
        } else {
            return englishDict.getCompletions(prefix, 20);
        }
    }

    /**
     * Learn from user input
     */
    public void learn(String word, String context) {
        if (word == null || word.isEmpty()) return;

        userModel.addWord(word, context);
        ngramModel.updateBigram(context, word);
    }
}
```

---

## Phase 4: Integration (Week 5)

### Step 4.1: Modify InputLogic.java

```java
// In InputLogic.java

public class InputLogic {
    private PredictionEngine predictionEngine;
    private StringBuilder currentWord = new StringBuilder();
    private String previousWord = "";

    public void startInput() {
        // ... existing code ...

        // Initialize prediction engine if not already done
        if (predictionEngine == null) {
            predictionEngine = new PredictionEngine(mLatinIME.getApplicationContext());
        }

        currentWord.setLength(0);
        previousWord = "";
    }

    private void handleCharacter(final int codePoint) {
        // Add to current word buffer
        currentWord.append(Character.toChars(codePoint));

        // Get current layout
        LayoutDetector.KeyboardLayout layout =
            LayoutDetector.detectLayout(mLatinIME.getCurrentSubtype());

        // Get predictions
        List<Suggestion> suggestions = predictionEngine.predict(
            currentWord.toString(),
            previousWord,
            layout
        );

        // Update suggestion strip
        mLatinIME.updateSuggestionStrip(suggestions);
    }

    private void handleSeparator(final int codePoint) {
        // Space/punctuation pressed - commit current word
        String finalWord = currentWord.toString();

        if (!finalWord.isEmpty()) {
            // Learn from this input
            predictionEngine.learn(finalWord, previousWord);
            previousWord = finalWord;
        }

        currentWord.setLength(0);
    }

    // Called when user picks a suggestion
    public void onSuggestionPicked(Suggestion suggestion) {
        mConnection.commitText(suggestion.getWord(), 1);
        predictionEngine.learn(suggestion.getWord(), previousWord);

        previousWord = suggestion.getWord();
        currentWord.setLength(0);
    }
}
```

---

## Testing Plan

### Unit Tests

```java
// LayoutDetectorTest.java
@Test
public void testPhoneticLayoutDetection() {
    Subtype subtype = createSubtype(SubtypeLocaleUtils.LAYOUT_KANNADA_PHONETIC);
    assertEquals(KeyboardLayout.PHONETIC, LayoutDetector.detectLayout(subtype));
}

// TrieDictTest.java
@Test
public void testCompletions() {
    TrieDict dict = new TrieDict();
    dict.insert("ಕನ್ನಡ", 100);
    dict.insert("ಕರ್ನಾಟಕ", 80);

    List<String> results = dict.getCompletions("ಕ", 10);
    assertEquals(2, results.size());
    assertEquals("ಕನ್ನಡ", results.get(0)); // Higher frequency first
}

// PredictionEngineTest.java
@Test
public void testCrossLayoutPrediction() {
    // Type "ka" in phonetic
    List<Suggestion> phoneticResults = engine.predict("ka", "", PHONETIC);

    // Type "ಕ" in standard
    List<Suggestion> standardResults = engine.predict("ಕ", "", STANDARD);

    // Should suggest similar words (both start with ಕ)
    assertTrue(phoneticResults.get(0).getWord().startsWith("ಕ"));
    assertTrue(standardResults.get(0).getWord().startsWith("ಕ"));
}
```

---

## MVP Checklist (Minimum Viable Product)

### Week 1-2: Foundation
- [x] LayoutDetector.java
- [x] Suggestion.java
- [ ] InputNormalizer.java
- [ ] UserDatabaseHelper.java
- [ ] UserLearningModel.java (basic SQLite operations)

### Week 3: Dictionary
- [ ] Download/extract Kannada word list (10k+ words)
- [ ] Build TrieDict.java
- [ ] Create binary dictionary format
- [ ] Load dictionary on app startup

### Week 4: Core Engine
- [ ] PredictionEngine.java (dictionary-based suggestions only)
- [ ] SuggestionRanker.java (basic frequency ranking)
- [ ] Integrate with InputLogic.java

### Week 5: Testing & Polish
- [ ] Unit tests for core components
- [ ] Manual testing across all layouts
- [ ] Performance optimization (caching, lazy loading)
- [ ] Basic user learning (add words to SQLite)

### Future Enhancements (Post-MVP)
- [ ] N-gram model for context-aware predictions
- [ ] English dictionary integration
- [ ] Bilingual suggestions for phonetic layout
- [ ] Advanced ranking (recency, edit distance)
- [ ] Settings UI (enable/disable learning, clear data)
- [ ] Export/import user dictionary

---

## Quick Reference: File Locations

### Source Code
- `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/prediction/`

### Assets (Data Files)
- `app/src/main/assets/dictionaries/`
  - `kannada_base.dict` (binary)
  - `english_base.dict` (binary)
  - `bigrams.bin` (binary)

### User Data (Runtime)
- `app/data/data/rkr.simplekeyboard/databases/user_learned.db`

### Build Tools (Offline Processing)
- `tools/DictionaryBuilder.java`
- `tools/corpus_processor.py`

---

## Performance Targets

- **Initialization**: < 500ms (async background load)
- **Prediction Latency**: < 50ms per keystroke
- **Memory Usage**: < 30MB total (dictionaries + runtime)
- **Storage**: < 5MB (base dictionaries)
- **User Data**: < 2MB (auto-pruned to 10k words max)

---

## Resources & References

### Kannada Wordlists
- Mozilla Gaia Keyboard: https://github.com/mozilla-b2g/gaia
- Kannada Wikipedia: https://dumps.wikimedia.org/knwiki/
- TDIL Indian Languages: http://tdil-dc.in/

### N-gram Corpora
- IIT Bombay CFILT: http://www.cfilt.iitb.ac.in/
- ILCI Kannada Corpus: http://sanskrit.jnu.ac.in/ilci/

### Algorithms
- Trie data structure: https://en.wikipedia.org/wiki/Trie
- Edit distance (Levenshtein): https://en.wikipedia.org/wiki/Levenshtein_distance
- Language model n-grams: https://en.wikipedia.org/wiki/N-gram

---

## Next Steps

1. ✅ Review design document (`PREDICTION_ENGINE_DESIGN.md`)
2. ⬜ Set up development environment
3. ⬜ Create package structure
4. ⬜ Implement MVP components (Weeks 1-4)
5. ⬜ Build test dictionary (10k Kannada words)
6. ⬜ Integration testing
7. ⬜ Alpha release & user feedback

**Ready to start coding!** Begin with `InputNormalizer.java` and `UserDatabaseHelper.java` from Phase 1.
