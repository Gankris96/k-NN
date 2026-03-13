/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import lombok.SneakyThrows;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.FixedBitSet;
import org.opensearch.knn.KNNTestCase;
import org.opensearch.knn.index.vectorvalues.KNNVectorValues;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PrefetchStrategyTests extends KNNTestCase {

    // AllAtOncePrefetchStrategy tests

    @SneakyThrows
    public void testAllAtOnce_whenFirstCall_thenPrefetchCalled() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        int[] docIds = { 0, 1, 2 };
        AllAtOncePrefetchStrategy strategy = new AllAtOncePrefetchStrategy(vectorValues, docIds);

        strategy.maybePrefetch(0);

        verify(vectorValues).prefetchByDocIds(docIds);
    }

    @SneakyThrows
    public void testAllAtOnce_whenCalledMultipleTimes_thenPrefetchCalledOnlyOnce() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        int[] docIds = { 0, 1, 2 };
        AllAtOncePrefetchStrategy strategy = new AllAtOncePrefetchStrategy(vectorValues, docIds);

        strategy.maybePrefetch(0);
        strategy.maybePrefetch(1);
        strategy.maybePrefetch(2);

        verify(vectorValues).prefetchByDocIds(docIds);
    }

    // BatchedBitSetPrefetchStrategy tests

    @SneakyThrows
    public void testBatched_whenFirstCall_thenPrefetchCalledWithBatch() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        FixedBitSet bitSet = new FixedBitSet(10);
        bitSet.set(0);
        bitSet.set(1);
        bitSet.set(5);
        BatchedBitSetPrefetchStrategy strategy = new BatchedBitSetPrefetchStrategy(bitSet, vectorValues);

        strategy.maybePrefetch(0);

        verify(vectorValues).prefetchByDocIds(new int[] { 0, 1, 5 });
    }

    @SneakyThrows
    public void testBatched_whenWithinBatch_thenNoPrefetchAgain() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        FixedBitSet bitSet = new FixedBitSet(10);
        bitSet.set(0);
        bitSet.set(1);
        bitSet.set(5);
        BatchedBitSetPrefetchStrategy strategy = new BatchedBitSetPrefetchStrategy(bitSet, vectorValues);

        strategy.maybePrefetch(0);
        strategy.maybePrefetch(1);
        strategy.maybePrefetch(5);

        // Only one prefetch call for the entire batch
        verify(vectorValues).prefetchByDocIds(new int[] { 0, 1, 5 });
    }

    @SneakyThrows
    public void testBatched_whenCrossesBatchBoundary_thenPrefetchesNextBatch() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        int totalDocs = BatchedBitSetPrefetchStrategy.BATCH_SIZE + 10;
        FixedBitSet bitSet = new FixedBitSet(totalDocs);
        // Set all bits so we have more than BATCH_SIZE docs
        for (int i = 0; i < totalDocs; i++) {
            bitSet.set(i);
        }
        BatchedBitSetPrefetchStrategy strategy = new BatchedBitSetPrefetchStrategy(bitSet, vectorValues);

        // First call triggers first batch
        strategy.maybePrefetch(0);
        verify(vectorValues).prefetchByDocIds(any());

        // Call with docId beyond first batch boundary triggers second batch
        strategy.maybePrefetch(BatchedBitSetPrefetchStrategy.BATCH_SIZE + 1);
        // Should have been called twice total
        verify(vectorValues, org.mockito.Mockito.times(2)).prefetchByDocIds(any());
    }

    @SneakyThrows
    public void testBatched_whenEmptyBitSet_thenNoPrefetch() {
        KNNVectorValues<?> vectorValues = mock(KNNVectorValues.class);
        FixedBitSet bitSet = new FixedBitSet(10);
        BatchedBitSetPrefetchStrategy strategy = new BatchedBitSetPrefetchStrategy(bitSet, vectorValues);

        strategy.maybePrefetch(0);

        verify(vectorValues, never()).prefetchByDocIds(any());
    }

    // NOOP strategy test

    @SneakyThrows
    public void testNoop_whenCalled_thenNoPrefetch() {
        PrefetchStrategy noop = PrefetchStrategy.NOOP;
        // Should not throw
        noop.maybePrefetch(0);
        noop.maybePrefetch(DocIdSetIterator.NO_MORE_DOCS);
    }
}
