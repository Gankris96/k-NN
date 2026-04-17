/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSet;
import org.opensearch.knn.index.vectorvalues.KNNVectorValues;

import java.io.IOException;
import java.util.Arrays;

/**
 * A {@link PrefetchStrategy} that walks a BitSet in batches, prefetching vector data
 * for the next batch of doc IDs when the current doc crosses the batch boundary.
 */
class BatchedBitSetPrefetchStrategy implements PrefetchStrategy {
    static final int BATCH_SIZE = 1024;

    private final BitSet bitSet;
    private final KNNVectorValues<?> vectorValues;
    private int nextBitToScan;
    // batchUpperBoundDocId represents the docId upper bound of the batch
    private int batchUpperBoundDocId = -1;

    BatchedBitSetPrefetchStrategy(final BitSet bitSet, final KNNVectorValues<?> vectorValues) {
        this.bitSet = bitSet;
        this.vectorValues = vectorValues;
    }

    @Override
    public void maybePrefetch(final int currentDocId) throws IOException {
        if (currentDocId <= batchUpperBoundDocId) {
            return;
        }
        final int[] batch = new int[BATCH_SIZE];
        int count = 0;
        int docId = bitSet.nextSetBit(nextBitToScan);
        while (docId != DocIdSetIterator.NO_MORE_DOCS && count < BATCH_SIZE) {
            batch[count++] = docId;
            docId = docId + 1 < bitSet.length() ? bitSet.nextSetBit(docId + 1) : DocIdSetIterator.NO_MORE_DOCS;
        }
        if (count == 0) {
            return;
        }
        nextBitToScan = batch[count - 1] + 1;
        batchUpperBoundDocId = batch[count - 1];
        vectorValues.prefetchByDocIds(count == BATCH_SIZE ? batch : Arrays.copyOf(batch, count));
    }
}
