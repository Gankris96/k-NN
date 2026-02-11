/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues;

import java.io.IOException;
import java.util.Arrays;

/**
 * Concrete implementation of {@link KNNVectorValues} that returns float[] as vector and provides an abstraction over
 * {@link BinaryDocValues}, {@link FloatVectorValues}, {@link KnnFieldVectorsWriter} etc.
 */
public class KNNFloatVectorValues extends KNNVectorValues<float[]> {
    KNNFloatVectorValues(final KNNVectorValuesIterator vectorValuesIterator) {
        super(vectorValuesIterator);
    }

    @Override
    public float[] getVector() throws IOException {
        final float[] vector = VectorValueExtractorStrategy.extractFloatVector(vectorValuesIterator);
        this.dimension = vector.length;
        this.bytesPerVector = vector.length * 4;
        return vector;
    }

    // read one vector and then use that to prefetch other ordinals
    public void prefetch(final int[] ordsToPrefetch) throws IOException {
         if (vectorValuesIterator instanceof  KNNVectorValuesIterator.DocIdsIteratorValues docIdsIteratorValues) {
             final KnnVectorValues knnVectorValues = docIdsIteratorValues.getKnnVectorValues();
             if (knnVectorValues != null) {
                 knnVectorValues.prefetch(ordsToPrefetch);
             }
         }
    }



    // convert docIds to ords and then prefetch
    public void prefetchByDocIds(final int[] sortedDocIds) throws IOException {
        if (sortedDocIds == null || sortedDocIds.length == 0) {
            return;
        }
        if (!(vectorValuesIterator instanceof KNNVectorValuesIterator.DocIdsIteratorValues docIdsIteratorValues)) {
            return;
        }
        final KnnVectorValues knnVectorValues = docIdsIteratorValues.getKnnVectorValues();
        if (knnVectorValues == null) {
            return;
        }

        // copy the iterator and use it to fetch docIDs to ord
        final KnnVectorValues knnVectorValuesCopy = knnVectorValues.copy();
        final KnnVectorValues.DocIndexIterator ordIterator = knnVectorValuesCopy.iterator();
        final int[] ordsToPrefetch = new int[sortedDocIds.length];
        for (int i = 0; i < sortedDocIds.length; i++) {
            ordIterator.advance(sortedDocIds[i]);
            ordsToPrefetch[i] = ordIterator.index();
        }

        knnVectorValues.prefetch(ordsToPrefetch);
    }
    @Override
    public float[] conditionalCloneVector() throws IOException {
        float[] vector = getVector();
        if (vectorValuesIterator.getDocIdSetIterator() instanceof KnnVectorValues.DocIndexIterator) {
            return Arrays.copyOf(vector, vector.length);
        }
        return vector;
    }
}
