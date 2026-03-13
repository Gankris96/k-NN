/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSetIterator;
import org.opensearch.common.Nullable;
import org.opensearch.knn.index.query.TopDocsDISI;
import org.opensearch.knn.index.query.iterators.GroupedNestedDocIdSetIterator;
import org.opensearch.knn.index.vectorvalues.KNNVectorValues;
import org.opensearch.knn.index.vectorvalues.KNNVectorValuesFactory;

import java.io.IOException;

/**
 * Factory that creates the appropriate {@link PrefetchStrategy} based on the iterator type.
 * All iterator-type-specific logic for prefetch is contained here.
 */
class PrefetchStrategyFactory {

    private PrefetchStrategyFactory() {}

    /**
     * Creates a {@link PrefetchStrategy} for the given iterator.
     *
     * @param iterator the matched docs iterator (may be null)
     * @param fieldInfo the field info for the vector field
     * @param reader the segment reader
     * @return the appropriate prefetch strategy
     */
    static PrefetchStrategy create(@Nullable final DocIdSetIterator iterator, final FieldInfo fieldInfo, final SegmentReader reader)
        throws IOException {
        return switch (iterator) {
            case TopDocsDISI topDocsDISI -> {
                final KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
                yield new AllAtOncePrefetchStrategy(vectorValues, topDocsDISI.getSortedDocIds());
            }
            case BitSetIterator bitSetIterator -> {
                final KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
                yield new BatchedBitSetPrefetchStrategy(bitSetIterator.getBitSet(), vectorValues);
            }
            case GroupedNestedDocIdSetIterator groupedIterator -> {
                final KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
                yield new AllAtOncePrefetchStrategy(vectorValues, groupedIterator.getExpandedDocIds());
            }
            case null, default -> PrefetchStrategy.NOOP;
        };
    }
}
