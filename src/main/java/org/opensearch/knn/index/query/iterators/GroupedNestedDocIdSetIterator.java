/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.iterators;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A `DocIdSetIterator` that iterates over all nested document IDs belongs to the same parent document for a given
 * set of nested document IDs.
 *
 * It is permissible for {@link #docIds} to contain multiple nested document IDs linked to a single parent document.
 * In such cases, this iterator will still iterate over each nested document ID only once.
 */
public class GroupedNestedDocIdSetIterator extends DocIdSetIterator {
    private final BitSet parentBitSet;
    private final Bits filterBits;
    private final List<Integer> docIds;
    private int[] expandedDocIds;
    private int currentIndex;
    private int currentDocId;
    private int currentParentId;

    public GroupedNestedDocIdSetIterator(final BitSet parentBitSet, final Set<Integer> docIds, final Bits filterBits) {
        this.parentBitSet = parentBitSet;
        this.docIds = new ArrayList<>(docIds);
        this.docIds.sort(Comparator.naturalOrder());
        this.filterBits = filterBits;
        currentIndex = -1;
        currentDocId = -1;
    }

    @Override
    public int docID() {
        return currentDocId;
    }

    @Override
    public int nextDoc() throws IOException {
        while (true) {
            if (doNextDoc() != NO_MORE_DOCS) {
                if (!filterBits.get(currentDocId)) {
                    continue;
                }

                return currentDocId;
            }

            return currentDocId;
        }
    }

    public int doNextDoc() throws IOException {
        if (currentDocId == NO_MORE_DOCS) {
            return currentDocId;
        }

        if (currentDocId == -1) {
            moveToNextIndex();
            return currentDocId;
        }

        currentDocId++;
        assert currentDocId <= currentParentId;
        if (currentDocId == currentParentId) {
            moveToNextIndex();
        }
        return currentDocId;
    }

    @Override
    public int advance(final int i) throws IOException {
        if (currentDocId == NO_MORE_DOCS) {
            return currentDocId;
        }

        return slowAdvance(i);
    }

    @Override
    public long cost() {
        return getExpandedDocIds().length;
    }

    /**
     * Returns all child doc IDs that this iterator will visit, in sorted order,
     * without consuming the iterator. Computed lazily and cached.
     */
    public int[] getExpandedDocIds() {
        if (expandedDocIds == null) {
            expandedDocIds = computeExpandedDocIds();
        }
        return expandedDocIds;
    }

    private int[] computeExpandedDocIds() {
        final List<Integer> result = new ArrayList<>();
        int lastDocId = -1;
        for (int docId : docIds) {
            if (docId < lastDocId) {
                continue;
            }

            for (lastDocId = parentBitSet.prevSetBit(docId) + 1; lastDocId < parentBitSet.nextSetBit(docId); lastDocId++) {
                if (filterBits.get(lastDocId)) {
                    result.add(lastDocId);
                }
            }
        }
        final int[] expandedDocIdsArray = new int[result.size()];
        for (int i = 0; i < result.size(); i++) {
            expandedDocIdsArray[i] = result.get(i);
        }
        return expandedDocIdsArray;
    }

    private void moveToNextIndex() {
        currentIndex++;
        while (currentIndex < docIds.size()) {
            // Advance currentIndex until the docId at the currentIndex is greater than currentDocId.
            // This ensures proper handling when docIds contain multiple entries under the same parent ID
            // that have already been iterated.
            if (docIds.get(currentIndex) <= currentDocId) {
                currentIndex++;
                continue;
            }
            currentDocId = parentBitSet.prevSetBit(docIds.get(currentIndex)) + 1;
            currentParentId = parentBitSet.nextSetBit(docIds.get(currentIndex));
            assert currentParentId != NO_MORE_DOCS;
            return;
        }
        currentDocId = NO_MORE_DOCS;
    }
}
