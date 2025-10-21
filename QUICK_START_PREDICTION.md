# Quick Start Guide: Bilingual Prediction Engine

## 📋 Overview

This guide helps you get the **English + Kannada prediction engine** up and running quickly.

---

## ✅ What's Already Done

1. **Core Components Created**:
   - ✅ `LayoutDetector.java` - Detects keyboard layout (QWERTY/Phonetic/Standard/Custom)
   - ✅ `Suggestion.java` - Data structure for word suggestions
   - ✅ `LanguageDetector.java` - Auto-detects English vs Kannada text

2. **Design Documents**:
   - ✅ `PREDICTION_ENGINE_DESIGN.md` - Complete architecture
   - ✅ `BILINGUAL_PREDICTION_DESIGN.md` - English + Kannada integration
   - ✅ `IMPLEMENTATION_ROADMAP.md` - Step-by-step guide

---

## 🚀 Getting Started (30 Minutes)

### Step 1: Download English Dictionary (5 min)

```bash
# Navigate to project root
cd C:\Users\Nags\AndroidStudioProjects\simple-keyboard

# Create tools directory
mkdir tools
cd tools

# Download Peter Norvig's frequency list (30MB compressed)
# Option A: Direct download
Invoke-WebRequest -Uri "http://norvig.com/ngrams/count_1w.txt" -OutFile "count_1w.txt"

# Option B: Use smaller Google 10k word list (for testing)
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-usa.txt" -OutFile "english_10k.txt"
```

### Step 2: Download Kannada Dictionary (5 min)

```bash
# Option A: Mozilla Gaia keyboard (best quality)
git clone --depth 1 https://github.com/mozilla-b2g/gaia.git
# Extract from: gaia/apps/keyboard/js/imes/kannada/

# Option B: Use existing Kannada wordlist from Wikipedia
# We'll create a basic list in Step 3
```

### Step 3: Create Basic Dictionaries (10 min)

Create `tools/build_dicts.py`:

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Quick dictionary builder for English + Kannada
"""

def build_english_dict():
    """Build English dictionary from count_1w.txt or english_10k.txt"""
    words = []

    # Try full list first, fall back to 10k
    try:
        with open('count_1w.txt', 'r', encoding='utf-8') as f:
            for line in f:
                parts = line.strip().split('\t')
                if len(parts) >= 2:
                    word = parts[0].lower()
                    freq = int(parts[1]) if parts[1].isdigit() else 1000

                    if word.isalpha() and 2 <= len(word) <= 20:
                        words.append((word, freq))

        words = words[:200000]  # Top 200k
    except FileNotFoundError:
        print("Using english_10k.txt fallback...")
        with open('english_10k.txt', 'r', encoding='utf-8') as f:
            for i, line in enumerate(f):
                word = line.strip().lower()
                if word.isalpha():
                    words.append((word, 10000 - i))  # Decreasing freq

    # Save
    with open('english_frequency.txt', 'w', encoding='utf-8') as f:
        for word, freq in words:
            f.write(f'{word}\t{freq}\n')

    print(f'Built English dictionary: {len(words)} words')

def build_kannada_dict():
    """Build basic Kannada dictionary (you'll expand this later)"""
    # Basic Kannada words for testing
    kannada_words = [
        ('ಕನ್ನಡ', 10000),
        ('ನಮಸ್ಕಾರ', 9000),
        ('ಧನ್ಯವಾದ', 8000),
        ('ಕರ್ನಾಟಕ', 7500),
        ('ಬೆಂಗಳೂರು', 7000),
        ('ಹೇಗಿದ್ದೀರಿ', 6500),
        ('ಹೌದು', 6000),
        ('ಇಲ್ಲ', 5500),
        ('ದಯವಿಟ್ಟು', 5000),
        ('ಕ್ಷಮಿಸಿ', 4500),
        ('ಸರಿ', 4000),
        ('ಒಳ್ಳೆಯದು', 3500),
        ('ಮನೆ', 3000),
        ('ಕೆಲಸ', 2800),
        ('ಸಮಯ', 2600),
        ('ದಿನ', 2400),
        ('ರಾತ್ರಿ', 2200),
        ('ಬೆಳಿಗ್ಗೆ', 2000),
        ('ಸಂಜೆ', 1800),
        ('ಊಟ', 1600),
    ]

    with open('kannada_frequency.txt', 'w', encoding='utf-8') as f:
        for word, freq in kannada_words:
            f.write(f'{word}\t{freq}\n')

    print(f'Built Kannada dictionary: {len(kannada_words)} words (basic)')
    print('NOTE: Expand this list from Wikipedia or Mozilla sources later')

if __name__ == '__main__':
    build_english_dict()
    build_kannada_dict()
    print('\n✅ Dictionaries created:')
    print('   - english_frequency.txt')
    print('   - kannada_frequency.txt')
```

Run it:
```bash
python build_dicts.py
```

### Step 4: Create Assets Directory (2 min)

```bash
# Create directory structure
mkdir -p ../app/src/main/assets/dictionaries

# Copy dictionaries (we'll build binary format later)
cp english_frequency.txt ../app/src/main/assets/dictionaries/
cp kannada_frequency.txt ../app/src/main/assets/dictionaries/
```

### Step 5: Create Placeholder Classes (5 min)

Create the remaining prediction engine components as placeholders:

**File**: `app/src/main/java/.../prediction/InputNormalizer.java`

```java
package rkr.simplekeyboard.inputmethod.latin.prediction;

import java.text.Normalizer;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.latin.utils.KannadaTransliterator;

public class InputNormalizer {

    /**
     * Normalize input text based on keyboard layout.
     * Converts phonetic input to Kannada, normalizes Unicode forms, etc.
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
     */
    private String canonicalizeKannada(String text) {
        // Convert to NFC (Canonical Composition)
        // This combines base characters with combining marks
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }
}
```

**File**: `app/src/main/java/.../prediction/dictionary/TrieDict.java`

```java
package rkr.simplekeyboard.inputmethod.latin.prediction.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Trie-based dictionary for fast prefix matching.
 * Loads words from frequency-sorted text files.
 */
public class TrieDict {

    private TrieNode root;

    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isWordEnd = false;
        int frequency = 0;
    }

    public TrieDict() {
        this.root = new TrieNode();
    }

    /**
     * Load dictionary from text file (word\tfrequency format).
     */
    public void load(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, "UTF-8")
        );

        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\t");
            if (parts.length >= 1) {
                String word = parts[0];
                int frequency = parts.length >= 2 ?
                    Integer.parseInt(parts[1]) : 1000;

                insert(word, frequency);
                count++;
            }
        }

        System.out.println("Loaded " + count + " words into Trie");
    }

    /**
     * Insert a word with frequency into the Trie.
     */
    public void insert(String word, int frequency) {
        TrieNode node = root;

        for (char c : word.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
        }

        node.isWordEnd = true;
        node.frequency = frequency;
    }

    /**
     * Get word completions for a given prefix.
     */
    public List<String> getCompletions(String prefix, int maxResults) {
        TrieNode node = findNode(prefix);
        if (node == null) {
            return Collections.emptyList();
        }

        List<WordFreq> results = new ArrayList<>();
        collectWords(node, prefix, results, maxResults * 3);

        // Sort by frequency (descending) and return top N
        results.sort((a, b) -> b.frequency - a.frequency);

        List<String> words = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, results.size()); i++) {
            words.add(results.get(i).word);
        }

        return words;
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
            collectWords(entry.getValue(),
                        prefix + entry.getKey(),
                        results, limit);
        }
    }

    static class WordFreq {
        String word;
        int frequency;
        WordFreq(String w, int f) {
            word = w;
            frequency = f;
        }
    }
}
```

### Step 6: Create Simple Test (3 min)

Create `app/src/androidTest/java/.../prediction/TrieDictTest.java`:

```java
package rkr.simplekeyboard.inputmethod.latin.prediction;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.prediction.dictionary.TrieDict;

public class TrieDictTest {

    @Test
    public void testBasicCompletion() throws Exception {
        TrieDict dict = new TrieDict();

        // Create test dictionary
        String testData = "hello\t1000\nhelp\t900\nheld\t800\nworld\t500\n";
        InputStream stream = new ByteArrayInputStream(testData.getBytes("UTF-8"));

        dict.load(stream);

        // Test completions
        List<String> results = dict.getCompletions("hel", 10);

        assertEquals(3, results.size());
        assertEquals("hello", results.get(0));  // Highest frequency
        assertEquals("help", results.get(1));
        assertEquals("held", results.get(2));
    }

    @Test
    public void testKannadaCompletion() throws Exception {
        TrieDict dict = new TrieDict();

        String testData = "ಕನ್ನಡ\t10000\nಕರ್ನಾಟಕ\t8000\nಕೆಲಸ\t5000\n";
        InputStream stream = new ByteArrayInputStream(testData.getBytes("UTF-8"));

        dict.load(stream);

        List<String> results = dict.getCompletions("ಕ", 10);

        assertEquals(3, results.size());
        assertTrue(results.get(0).startsWith("ಕ"));
    }
}
```

---

## 🎯 Next Steps (After Quick Start)

### Immediate (This Week)
1. ✅ Run the quick start steps above
2. ⬜ Test TrieDict with basic dictionaries
3. ⬜ Expand Kannada dictionary (extract from Mozilla or Wikipedia)
4. ⬜ Implement basic PredictionEngine (dictionary-only, no n-grams yet)

### Short-term (Next 2 Weeks)
1. ⬜ Integrate PredictionEngine with InputLogic.java
2. ⬜ Add suggestion strip UI updates
3. ⬜ Test on all keyboard layouts
4. ⬜ Implement user learning (SQLite database)

### Long-term (Next Month)
1. ⬜ Build n-gram models (bigrams/trigrams)
2. ⬜ Implement advanced ranking algorithm
3. ⬜ Add settings for user control
4. ⬜ Performance optimization

---

## 📦 Resources

### English Dictionaries
- **Peter Norvig's 1M words**: http://norvig.com/ngrams/
- **Google 10k**: https://github.com/first20hours/google-10000-english
- **SCOWL**: http://wordlist.aspell.net/

### Kannada Dictionaries
- **Mozilla Gaia**: https://github.com/mozilla-b2g/gaia
- **Kannada Wikipedia**: https://dumps.wikimedia.org/knwiki/
- **TDIL**: http://tdil-dc.in/

### Tools
- **WikiExtractor**: https://github.com/attardi/wikiextractor
- **N-gram Builder**: https://github.com/maxbane/simplelm

---

## 🐛 Troubleshooting

### "Dictionary file not found"
```
Error: FileNotFoundException: dictionaries/english_frequency.txt

Fix: Ensure files are in app/src/main/assets/dictionaries/
Verify with: ls app/src/main/assets/dictionaries/
```

### "Out of memory loading dictionary"
```
Error: OutOfMemoryError

Fix: Load dictionaries asynchronously in background thread
See: PredictionEngine.initializeAsync()
```

### "Kannada characters not displaying"
```
Issue: Seeing boxes instead of Kannada text

Fix: Ensure UTF-8 encoding in all file operations:
  - new InputStreamReader(stream, "UTF-8")
  - Files saved with UTF-8 encoding
```

---

## ✅ Checklist

- [ ] Downloaded English dictionary (count_1w.txt or english_10k.txt)
- [ ] Created basic Kannada dictionary (kannada_frequency.txt)
- [ ] Copied dictionaries to assets/dictionaries/
- [ ] Created TrieDict.java
- [ ] Created InputNormalizer.java
- [ ] Ran TrieDictTest and verified it passes
- [ ] Ready to implement PredictionEngine!

---

## 🎓 Architecture Recap

```
Your Typing → LayoutDetector → InputNormalizer
                                      ↓
                         ┌────────────┴────────────┐
                         │                         │
                   TrieDict (EN)            TrieDict (KN)
                         │                         │
                         └────────────┬────────────┘
                                      ↓
                            SuggestionRanker
                                      ↓
                          Top 5 Suggestions → UI
```

**You're now ready to build the prediction engine!** 🚀

Start with implementing `PredictionEngine.java` using the dictionaries you just created.
