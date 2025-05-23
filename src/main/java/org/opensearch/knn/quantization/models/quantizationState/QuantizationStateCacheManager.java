/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.quantization.models.quantizationState;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.opensearch.knn.index.codec.KNN990Codec.KNN990QuantizationStateReader;

import java.io.Closeable;
import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuantizationStateCacheManager implements Closeable {

    private static volatile QuantizationStateCacheManager instance;

    /**
     * Gets the singleton instance of the cache.
     * @return QuantizationStateCache
     */
    public static QuantizationStateCacheManager getInstance() {
        if (instance == null) {
            synchronized (QuantizationStateCacheManager.class) {
                if (instance == null) {
                    instance = new QuantizationStateCacheManager();
                }
            }
        }
        return instance;
    }

    public synchronized void rebuildCache() {
        QuantizationStateCache.getInstance().rebuildCache();
    }

    /**
     * Retrieves the quantization state associated with a given field name.  Reads from cache first, then from disk if necessary.
     * @param quantizationStateReadConfig information required from reading from off-heap if necessary
     * @return The associated QuantizationState
     */
    public QuantizationState getQuantizationState(QuantizationStateReadConfig quantizationStateReadConfig) throws IOException {
        final QuantizationState quantizationState = QuantizationStateCache.getInstance()
            .getQuantizationState(
                quantizationStateReadConfig.getCacheKey(),
                () -> KNN990QuantizationStateReader.read(quantizationStateReadConfig)
            );
        return quantizationState;
    }

    /**
     * Removes the quantization state associated with a given field name.
     * @param fieldName The name of the field.
     */
    public void evict(String fieldName) {
        QuantizationStateCache.getInstance().evict(fieldName);
    }

    public void setMaxCacheSizeInKB(long maxCacheSizeInKB) {
        QuantizationStateCache.getInstance().setMaxCacheSizeInKB(maxCacheSizeInKB);
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        QuantizationStateCache.getInstance().clear();
    }

    @Override
    public void close() throws IOException {
        QuantizationStateCache.getInstance().close();
    }
}
