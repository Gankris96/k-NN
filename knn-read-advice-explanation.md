# OpenSearch k-NN: Read Advice Optimization - Complete Explanation

## Table of Contents
1. [Background: What is OpenSearch k-NN?](#background)
2. [Understanding Search Engines](#search-engines)
3. [Search Types: Approximate vs Exact](#search-types)
4. [The .vec File](#vec-file)
5. [Operating System Read Patterns](#os-read-patterns)
6. [The Problem](#the-problem)
7. [When Does Exact Search Happen?](#exact-search-scenarios)
8. [The Solution](#the-solution)
9. [Understanding Codecs](#understanding-codecs)
10. [Implementation Details](#implementation-details)

---

## Background: What is OpenSearch k-NN? {#background}

OpenSearch k-NN is a plugin that enables nearest neighbor search - finding vectors (data points) that are similar to a query vector. 

**Use Cases:**
- "Find products similar to this one"
- "Find documents similar to this query"
- Image/video similarity search
- Recommendation systems

---

## Understanding Search Engines {#search-engines}

The k-NN plugin supports multiple vector search engines:

### Lucene Engine
- Built into OpenSearch
- Uses HNSW (Hierarchical Navigable Small World) graphs
- Approximate search method

### Faiss Engine
- Facebook's similarity search library
- Supports both approximate and exact search
- **This is the focus of the optimization**

### NMSLIB Engine
- Non-Metric Space Library
- Another approximate search option

---

## Search Types: Approximate vs Exact {#search-types}

### Approximate Search (HNSW)
- **Method**: Uses a graph structure to quickly find "close enough" neighbors
- **Access Pattern**: RANDOM - jumps around the file reading different parts
- **Speed**: Fast
- **Accuracy**: ~95-99% accurate

```
Graph Navigation:
Start → Node 5 → Node 23 → Node 89 → Node 12 → Result
        ↓         ↓          ↓          ↓
     Random    Random     Random     Random
     Access    Access     Access     Access
```

### Exact Search (Brute Force)
- **Method**: Compares query vector against EVERY vector in the dataset
- **Access Pattern**: SEQUENTIAL - reads the file from start to end
- **Speed**: Slower
- **Accuracy**: 100% accurate

```
Sequential Scan:
Vector 1 → Vector 2 → Vector 3 → ... → Vector N
   ↓          ↓          ↓                ↓
Compare    Compare    Compare         Compare
```

---

## The .vec File {#vec-file}

This file stores the actual vector data (raw numbers representing each data point).

**Example**: If you have 1 million products, each represented as a 768-dimensional vector:
```
.vec file contains:
Product 1: [0.23, 0.45, 0.12, ..., 0.89]  (768 numbers)
Product 2: [0.67, 0.34, 0.91, ..., 0.23]  (768 numbers)
...
Product 1M: [0.45, 0.78, 0.34, ..., 0.56] (768 numbers)
```

**File Size**: For 1M vectors × 768 dimensions × 4 bytes = ~3GB

---

## Operating System Read Patterns {#os-read-patterns}

When a program reads a file, it tells the OS how it plans to access it:

### RANDOM Read Advice
```
Program: "I'll jump around the file randomly"
OS Response:
  ✗ Disables read-ahead (pre-fetching)
  ✗ Different caching strategy
  ✗ Reads only what's requested (e.g., 4KB at a time)

Good for: Graph traversal, database lookups
```

### NORMAL Read Advice
```
Program: "I'll read sequentially or in typical patterns"
OS Response:
  ✓ Enables read-ahead
  ✓ When you read byte 1000, pre-loads bytes 1001-2000
  ✓ Better caching for sequential access
  ✓ Reads larger chunks (e.g., 128KB at a time)

Good for: Scanning files, processing data in order
```

### Performance Impact Example
For a 1GB .vec file scanned sequentially:
- **RANDOM advice**: 262,144 read operations (4KB each)
- **NORMAL advice**: ~8,192 read operations (128KB each with read-ahead)

**Result**: NORMAL advice can be 10-30x faster for sequential scans!

---

## The Problem {#the-problem}

### The Issue Flow

```
┌─────────────────────────────────────────────────────────────┐
│  k-NN Plugin (Faiss Engine)                                  │
│  "I need to read vectors from .vec file"                    │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Calls Lucene's File System API                              │
│  "Hey Lucene, open this .vec file for me"                   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Lucene Opens the File                                       │
│  Lucene says to OS: "Open with RANDOM read advice"          │
│  (This is HARDCODED in Lucene99FlatVectorsFormat)           │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Faiss Gets the File Handle                                  │
│  Now reads sequentially, but OS has RANDOM advice set       │
│  Result: Read-ahead is DISABLED                             │
└─────────────────────────────────────────────────────────────┘
```

### Why Lucene Uses RANDOM Advice

Lucene assumes `.vec` files are used for its own HNSW implementation, which:
- Jumps around randomly through vectors
- Benefits from RANDOM advice

**But**: Faiss exact search reads sequentially, so RANDOM advice hurts performance!

### Why k-NN Can't Just Change Lucene's Code

```java
// In Lucene's source code (external library)
public final class Lucene99FlatVectorsFormat extends FlatVectorsFormat {
    // ↑ final = Can't extend this class
    
    public FlatVectorsReader fieldsReader(SegmentReadState state) {
        // ↑ Can't override this method
        
        // RANDOM advice is hardcoded here:
        IndexInput input = state.directory.openInput(
            fileName, 
            new IOContext(ReadAdvice.RANDOM)  // ← HARDCODED!
        );
        return new Lucene99FlatVectorsReader(input);
    }
}
```

---

## When Does Exact Search Happen? {#exact-search-scenarios}

### 1. Skipped .faiss Building
The `.faiss` file contains the HNSW graph. If not built, only exact search is available.

### 2. High Filtering Cardinality
```
Query: "Find similar products WHERE price < $50"
If filter matches 80% of documents → Approximate search ineffective
Falls back to exact search
```

### 3. Low Approximate Results
If HNSW doesn't find enough good matches, exact search fills in results.

### 4. Disk-Based Index 2nd Phase
Some indices don't fit in memory. After approximate search, exact search refines results.

### 5. Script Search
```
Custom scoring using scripts:
for (vector in all_vectors) {
    score = custom_script(vector)
}
// Loops through all vectors sequentially
```

### 6. Radial Search
```
Query: "Find all vectors within distance 0.5"
May need exact search as fallback
```

---

## The Solution {#the-solution}

### High-Level Approach

Since k-NN can't change Lucene's code, it intercepts at the **Directory level** (before Lucene opens the file):

```
┌─────────────────────────────────────────────────────────────┐
│  k-NN Plugin                                                 │
│  "Give me a reader for .vec"                                │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Custom Directory Wrapper (FlagsOverridingMMapDirectory)     │
│  Intercepts file open requests                              │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Lucene Tries to Open .vec                                   │
│  "Open .vec with RANDOM advice"                             │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Directory Wrapper Intercepts!                               │
│  Checks: Is this a .vec file?                               │
│  YES → Changes RANDOM → NORMAL                              │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  Opens File with NORMAL Advice                               │
│  OS enables read-ahead                                       │
│  Faiss exact search now runs faster!                        │
└─────────────────────────────────────────────────────────────┘
```

### Implementation Code

```java
private static final class FlagsOverridingMMapDirectory extends FilterDirectory {
    private static final String VECTOR_DATA_EXTENSION = ".vec";
    private final boolean useNormal;
    
    @Override
    public IndexInput openInput(final String fileName, final IOContext context) 
            throws IOException {
        return in.openInput(fileName, createNormalIOContext(fileName, context));
    }
    
    private IOContext createNormalIOContext(final String fileName, 
                                           final IOContext defaultContext) {
        if (useNormal && fileName.endsWith(VECTOR_DATA_EXTENSION)) {
            // Force ReadAdvice.NORMAL for .vec files
            return new IOContext(ReadAdvice.NORMAL);
        }
        return defaultContext;  // Keep original for other files
    }
}
```

### Two-Way Door Solution

Make it configurable so it can be rolled back if issues arise:

**Dynamic Setting**: `index.knn.use_normal_read_advice_for_faiss` (default: true)

**Two Implementation Options**:

#### Option 1: Two Reader Instances
```java
public class NativeEngines990KnnVectorsReader extends KnnVectorsReader {
    private final FlatVectorsReader flatVectorsReaderNormal;  // NORMAL advice
    private FlatVectorsReader flatVectorsReaderRandom;        // RANDOM advice (lazy)
    
    public FloatVectorValues getFloatVectorValues(final String field) {
        if (useSetting == true) {
            return flatVectorsReaderNormal.getFloatVectorValues(field);
        } else {
            if (flatVectorsReaderRandom == null) {
                flatVectorsReaderRandom = createRandomReader();
            }
            return flatVectorsReaderRandom.getFloatVectorValues(field);
        }
    }
}
```

**Downside**: Uses more memory (two mapping tables in kernel)
- ~2 MB for 1 GB file
- ~200 MB for 100 GB file

#### Option 2: Runtime madvise Calls
```java
private void flipReadAdvice(boolean useNormal) {
    // Use reflection to get MemorySegment[]
    MemorySegment[] segments = extractSegments(vectorData);
    
    // Call madvise on each segment
    for (MemorySegment segment : segments) {
        NativeAccess.madvise(segment, useNormal ? NORMAL : RANDOM);
    }
}
```

**Advantage**: Single reader, less memory
**Disadvantage**: More complex, uses reflection

---

## Understanding Codecs {#understanding-codecs}

### What is a Codec?

**Codec = Coder/Decoder**

A codec defines **how data is written to disk and read back**. Think of it as a file format specification + the code to read/write that format.

### Why Do We Need Codecs?

When you index documents in OpenSearch/Lucene, different types of data need storage:
- Text content (inverted index)
- Document fields (stored fields)
- Term frequencies
- **Vectors** (for k-NN search) ← Our focus
- Norms, doc values, etc.

Each type has different access patterns and optimization needs.

### The Codec Hierarchy

```
Codec (Top Level)
├── PostingsFormat        → Terms and documents (inverted index)
├── StoredFieldsFormat    → Original document fields
├── DocValuesFormat       → Column-oriented data
├── KnnVectorsFormat      → Vector data (k-NN uses this!)
├── TermVectorsFormat     → Term positions
├── NormsFormat           → Field length norms
└── CompoundFormat        → Multiple files combined
```

### Files Created by the Codec

When you index vectors, the codec creates:

```
segment_0/
├── _0.vec          ← Raw vector data (Lucene's FlatVectorsFormat)
├── _0.vex          ← Vector metadata
├── _0.faiss        ← Faiss HNSW graph (k-NN plugin creates this)
├── _0.nmslib       ← NMSLIB graph (if using NMSLIB)
└── ... other Lucene files (postings, stored fields, etc.)
```

### k-NN Codec Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    KNN1030Codec                              │
│  (Main codec for OpenSearch k-NN with Lucene 10.3)          │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ extends FilterCodec
                            │ (wraps Lucene103Codec)
                            ▼
        ┌───────────────────────────────────────────┐
        │  Overrides specific formats:              │
        ├───────────────────────────────────────────┤
        │  • knnVectorsFormat()                     │
        │  • docValuesFormat()                      │
        │  • compoundFormat()                       │
        │  • storedFieldsFormat()                   │
        └───────────────────────────────────────────┘
                            │
                            │ For vectors specifically
                            ▼
        ┌───────────────────────────────────────────┐
        │   KnnVectorsFormat                        │
        │   (Handles vector storage)                │
        └───────────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────────┐
        │   PerFieldKnnVectorsFormat                │
        │   (Different format per field)            │
        └───────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
    ┌─────────────────────┐   ┌─────────────────────┐
    │ Lucene Engine       │   │ Native Engines      │
    │ (HNSW built-in)     │   │ (Faiss, NMSLIB)     │
    └─────────────────────┘   └─────────────────────┘
                                        │
                                        ▼
                        ┌───────────────────────────┐
                        │ NativeEngines990          │
                        │ KnnVectorsFormat          │
                        └───────────────────────────┘
                                        │
                        ┌───────────────┴───────────────┐
                        ▼                               ▼
            ┌─────────────────────┐       ┌─────────────────────┐
            │ Writer              │       │ Reader              │
            │ (Write vectors)     │       │ (Read vectors)      │
            └─────────────────────┘       └─────────────────────┘
                        │                               │
                        ▼                               ▼
            ┌─────────────────────┐       ┌─────────────────────┐
            │ Uses Lucene's       │       │ Uses Lucene's       │
            │ Lucene99Flat        │       │ Lucene99Flat        │
            │ VectorsFormat       │       │ VectorsFormat       │
            │ to write .vec file  │       │ to read .vec file   │
            └─────────────────────┘       └─────────────────────┘
```

### The Key Decision Logic

From `BasePerFieldKnnVectorsFormat.java`:

```java
@Override
public KnnVectorsFormat getKnnVectorsFormatForField(final String field) {
    // ... field validation ...
    
    final KNNEngine engine = knnMethodContext.getKnnEngine();
    
    if (engine == KNNEngine.LUCENE) {
        // Use Lucene's built-in HNSW
        return vectorsFormatSupplier.apply(knnVectorsFormatParams);
    }
    
    // All native engines (Faiss, NMSLIB) use this:
    return nativeEngineVectorsFormat();
}

private NativeEngines990KnnVectorsFormat nativeEngineVectorsFormat() {
    return new NativeEngines990KnnVectorsFormat(
        new Lucene99FlatVectorsFormat(...),  // ← Uses Lucene for .vec file
        approximateThreshold,
        nativeIndexBuildStrategyFactory
    );
}
```

### Complete Flow: User Creates Index to Search

```
┌──────────────────────────────────────────────────────────────────┐
│                    User Creates k-NN Index                        │
│                                                                   │
│  PUT /my-index                                                    │
│  {                                                                │
│    "mappings": {                                                  │
│      "properties": {                                              │
│        "my_vector": {                                             │
│          "type": "knn_vector",                                    │
│          "dimension": 768,                                        │
│          "method": {                                              │
│            "engine": "faiss",  ← CHOOSES ENGINE                   │
│            "name": "hnsw"                                         │
│          }                                                        │
│        }                                                          │
│      }                                                            │
│    }                                                              │
│  }                                                                │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│              OpenSearch Selects Codec                             │
│                                                                   │
│  KNN1030Codec.knnVectorsFormat()                                  │
│         ↓                                                         │
│  BasePerFieldKnnVectorsFormat.getKnnVectorsFormatForField()      │
│         ↓                                                         │
│  Checks: engine == "faiss"?                                       │
│         ↓                                                         │
│  YES → Returns NativeEngines990KnnVectorsFormat                   │
│         ↓                                                         │
│  This format wraps Lucene99FlatVectorsFormat                      │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│                    Indexing Vectors                               │
│                                                                   │
│  NativeEngines990KnnVectorsWriter                                 │
│         ↓                                                         │
│  Calls Lucene99FlatVectorsFormat.fieldsWriter()                   │
│         ↓                                                         │
│  Writes vectors to .vec file                                      │
│         ↓                                                         │
│  ALSO builds .faiss file (HNSW graph for Faiss)                   │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│                    Searching Vectors                              │
│                                                                   │
│  User searches: "Find similar vectors"                            │
│         ↓                                                         │
│  NativeEngines990KnnVectorsReader                                 │
│         ↓                                                         │
│  Calls Lucene99FlatVectorsFormat.fieldsReader()                   │
│         ↓                                                         │
│  Lucene opens .vec file with RANDOM advice ← THE PROBLEM!         │
│         ↓                                                         │
│  But Faiss exact search reads sequentially                        │
│         ↓                                                         │
│  Performance suffers due to disabled read-ahead                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Implementation Details {#implementation-details}

### Test Coverage Required

#### 1. Exact Search Paths
- Skipped .faiss building
- High filtering cardinality
- Low approximate results
- Disk-based index 2nd phase

#### 2. Script Search
Test with various filtering percentages:
- 0.1%, 1%, 5%, 10%, 25%, 50%, 75%, 90%, 95%, 99%

#### 3. Derived Source
With `_source: enabled` in query, full precision vectors are pulled from `.vec` file.
- This is random access pattern
- Need to measure performance impact

#### 4. Segment Merge
- Sequential read pattern
- Should see slight performance increase or same performance

#### 5. Radial Search
- Test fallback logic

### Performance Expectations

**Before (RANDOM advice)**:
```
Exact search on 1M vectors (3GB .vec file):
- 262,144 read operations (4KB each)
- ~15-30 seconds
```

**After (NORMAL advice)**:
```
Exact search on 1M vectors (3GB .vec file):
- ~8,192 read operations (128KB chunks with read-ahead)
- ~2-5 seconds
```

**Expected Improvement**: 5-10x faster for exact search operations

---

## Summary

### The Core Issue
Lucene optimizes `.vec` file access for random patterns (HNSW graphs), but Faiss exact search needs sequential patterns. Changing read advice from RANDOM to NORMAL enables OS read-ahead, significantly speeding up exact search operations.

### Why This Matters
- Exact search queries become 5-10x faster
- Script scoring improves significantly
- Filtered searches with fallback to exact search benefit
- Better resource utilization (fewer I/O operations)

### The Solution
- Wrap the Directory to intercept file opens
- Change read advice from RANDOM to NORMAL for `.vec` files
- Make it configurable (two-way door) for safety
- Comprehensive testing across all exact search scenarios

### Key Takeaways
1. **Codecs** define how data is stored and retrieved
2. **k-NN uses custom codecs** to support multiple engines
3. **Lucene's format is reused** for reliable vector storage
4. **Directory wrapper** provides the interception point
5. **Read advice matters** - can make 10-30x performance difference

---

## References

- [Original Quip Document](https://quip-amazon.com/yV0KATpeSubw)
- [GitHub PR #3044](https://github.com/opensearch-project/k-NN/pull/3044)
- [MMap Exact Search Performance Analysis](https://quip-amazon.com/oim9AdKI7Zn7)
