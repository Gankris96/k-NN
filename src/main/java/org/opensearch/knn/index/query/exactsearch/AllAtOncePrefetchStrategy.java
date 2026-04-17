/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.opensearch.knn.index.vectorvalues.KNNVectorValues;

import java.io.IOException;

/**
 * A {@link PrefetchStrategy} that prefetches all doc IDs on the first call.
 * Suitable for small, known sets of doc IDs (e.g., ANN rescore results from TopDocsDISI).
 */
class AllAtOncePrefetchStrategy implements PrefetchStrategy {
    private final KNNVectorValues<?> vectorValues;
    private final int[] docIds;
    private boolean prefetched;

    AllAtOncePrefetchStrategy(final KNNVectorValues<?> vectorValues, final int[] docIds) {
        this.vectorValues = vectorValues;
        this.docIds = docIds;
    }

    @Override
    public void maybePrefetch(final int currentDocId) throws IOException {
        if (!prefetched) {
            vectorValues.prefetchByDocIds(docIds);
            prefetched = true;
        }
    }
}
