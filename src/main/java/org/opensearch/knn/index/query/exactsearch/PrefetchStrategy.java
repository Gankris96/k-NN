/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import java.io.IOException;

/**
 * Strategy for prefetching vector data during exact search iteration.
 * Callers provide an appropriate implementation based on the iterator type.
 */
@FunctionalInterface
interface PrefetchStrategy {
    PrefetchStrategy NOOP = (docId) -> {};

    /**
     * Called once per doc during iteration. Implementations decide whether to
     * prefetch based on the current doc ID (e.g., batch boundary check, first-call-only, or no-op).
     *
     * @param currentDocId the doc ID about to be scored
     */
    void maybePrefetch(int currentDocId) throws IOException;
}
