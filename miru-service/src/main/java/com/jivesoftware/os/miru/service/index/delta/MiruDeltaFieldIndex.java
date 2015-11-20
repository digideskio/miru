package com.jivesoftware.os.miru.service.index.delta;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.primitives.UnsignedBytes;
import com.jivesoftware.os.filer.io.api.KeyRange;
import com.jivesoftware.os.miru.api.activity.schema.MiruFieldDefinition;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.TermIdStream;
import com.jivesoftware.os.miru.service.index.Mergeable;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.hash.TIntLongHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DELTA FORCE
 */
public class MiruDeltaFieldIndex<BM> implements MiruFieldIndex<BM>, Mergeable {

    private final MiruBitmaps<BM> bitmaps;
    private final long[] indexIds;
    private final MiruFieldIndex<BM> backingFieldIndex;
    private final ConcurrentSkipListMap<MiruTermId, MiruDeltaInvertedIndex.Delta<BM>>[] fieldIndexDeltas;
    private final ConcurrentHashMap<MiruTermId, TIntLongMap>[] cardinalities;
    private final Cache<IndexKey, Optional<?>> fieldIndexCache;
    private final Cache<MiruFieldIndex.IndexKey, Long> versionCache;
    private final AtomicLong version;

    private static final Comparator<MiruTermId> COMPARATOR = new Comparator<MiruTermId>() {

        private final Comparator<byte[]> lexicographicalComparator = UnsignedBytes.lexicographicalComparator();

        @Override
        public int compare(MiruTermId o1, MiruTermId o2) {
            return lexicographicalComparator.compare(o1.getBytes(), o2.getBytes());
        }
    };

    public MiruDeltaFieldIndex(MiruBitmaps<BM> bitmaps,
        long[] indexIds,
        MiruFieldIndex<BM> backingFieldIndex,
        MiruFieldDefinition[] fieldDefinitions,
        Cache<IndexKey, Optional<?>> fieldIndexCache,
        Cache<IndexKey, Long> versionCache,
        AtomicLong version) {
        this.bitmaps = bitmaps;
        this.indexIds = indexIds;
        this.backingFieldIndex = backingFieldIndex;
        this.fieldIndexDeltas = new ConcurrentSkipListMap[fieldDefinitions.length];
        this.cardinalities = new ConcurrentHashMap[fieldDefinitions.length];
        for (int i = 0; i < fieldDefinitions.length; i++) {
            fieldIndexDeltas[i] = new ConcurrentSkipListMap<>(COMPARATOR);
            if (fieldDefinitions[i].type.hasFeature(MiruFieldDefinition.Feature.cardinality)) {
                cardinalities[i] = new ConcurrentHashMap<>();
            }
        }
        this.fieldIndexCache = fieldIndexCache;
        this.versionCache = versionCache;
        this.version = version;
    }

    @Override
    public long getVersion(int fieldId, MiruTermId termId) throws Exception {
        return versionCache.get(getIndexKey(fieldId, termId), version::incrementAndGet);
    }

    @Override
    public void append(int fieldId, MiruTermId termId, int[] ids, long[] counts, byte[] primitiveBuffer) throws Exception {
        getOrAllocate(fieldId, termId).append(primitiveBuffer, ids);
        putCardinalities(fieldId, termId, ids, counts, true, primitiveBuffer);
    }

    @Override
    public void set(int fieldId, MiruTermId termId, int[] ids, long[] counts, byte[] primitiveBuffer) throws Exception {
        getOrAllocate(fieldId, termId).set(primitiveBuffer, ids);
        putCardinalities(fieldId, termId, ids, counts, false, primitiveBuffer);
    }

    @Override
    public void remove(int fieldId, MiruTermId termId, int id, byte[] primitiveBuffer) throws Exception {
        MiruInvertedIndex<BM> got = get(fieldId, termId);
        got.remove(id, primitiveBuffer);
        putCardinalities(fieldId, termId, new int[]{id}, cardinalities[fieldId] != null ? new long[1] : null, false, primitiveBuffer);
    }

    @Override
    public void streamTermIdsForField(int fieldId, List<KeyRange> ranges, final TermIdStream termIdStream, byte[] primitiveBuffer) throws Exception {
        final Set<MiruTermId> indexKeys = fieldIndexDeltas[fieldId].keySet();
        if (ranges != null && !ranges.isEmpty()) {
            for (KeyRange range : ranges) {
                final Set<MiruTermId> rangeKeys = fieldIndexDeltas[fieldId].navigableKeySet()
                    .subSet(new MiruTermId(range.getStartInclusiveKey()), new MiruTermId(range.getStopExclusiveKey()));
                for (MiruTermId termId : rangeKeys) {
                    if (!termIdStream.stream(termId)) {
                        return;
                    }
                }
            }
        } else {
            for (MiruTermId termId : indexKeys) {
                if (!termIdStream.stream(termId)) {
                    return;
                }
            }
        }
        backingFieldIndex.streamTermIdsForField(fieldId, ranges, termId -> {
            if (termId != null) {
                if (!indexKeys.contains(termId)) {
                    if (!termIdStream.stream(termId)) {
                        return false;
                    }
                }
            }
            return true;
        }, primitiveBuffer);
    }

    @Override
    public MiruInvertedIndex<BM> get(int fieldId, MiruTermId termId) throws Exception {
        MiruDeltaInvertedIndex.Delta<BM> delta = fieldIndexDeltas[fieldId].get(termId);
        if (delta == null) {
            delta = new MiruDeltaInvertedIndex.Delta<>();
            MiruDeltaInvertedIndex.Delta<BM> existing = fieldIndexDeltas[fieldId].putIfAbsent(termId, delta);
            if (existing != null) {
                delta = existing;
            }
        }
        return new MiruDeltaInvertedIndex<>(bitmaps, backingFieldIndex.get(fieldId, termId), delta, getIndexKey(fieldId, termId),
            fieldIndexCache, versionCache);
    }

    private IndexKey getIndexKey(int fieldId, MiruTermId termId) {
        return new IndexKey(indexIds[fieldId], termId.getBytes());
    }

    @Override
    public MiruInvertedIndex<BM> get(int fieldId, MiruTermId termId, int considerIfIndexIdGreaterThanN) throws Exception {
        MiruDeltaInvertedIndex.Delta<BM> delta = fieldIndexDeltas[fieldId].get(termId);
        if (delta == null) {
            delta = new MiruDeltaInvertedIndex.Delta<>();
            MiruDeltaInvertedIndex.Delta<BM> existing = fieldIndexDeltas[fieldId].putIfAbsent(termId, delta);
            if (existing != null) {
                delta = existing;
            }
        }
        return new MiruDeltaInvertedIndex<>(bitmaps, backingFieldIndex.get(fieldId, termId, considerIfIndexIdGreaterThanN), delta,
            getIndexKey(fieldId, termId), fieldIndexCache, versionCache);
    }

    @Override
    public MiruInvertedIndex<BM> getOrCreateInvertedIndex(int fieldId, MiruTermId termId) throws Exception {
        return getOrAllocate(fieldId, termId);
    }

    private MiruInvertedIndex<BM> getOrAllocate(int fieldId, MiruTermId termId) throws Exception {
        MiruDeltaInvertedIndex.Delta<BM> delta = fieldIndexDeltas[fieldId].get(termId);
        if (delta == null) {
            delta = new MiruDeltaInvertedIndex.Delta<>();
            MiruDeltaInvertedIndex.Delta<BM> existing = fieldIndexDeltas[fieldId].putIfAbsent(termId, delta);
            if (existing != null) {
                delta = existing;
            }
        }
        return new MiruDeltaInvertedIndex<>(bitmaps, backingFieldIndex.getOrCreateInvertedIndex(fieldId, termId), delta, getIndexKey(fieldId, termId),
            fieldIndexCache, versionCache);
    }

    @Override
    public long getCardinality(int fieldId, MiruTermId termId, int id, byte[] primitiveBuffer) throws Exception {
        long[] result = {-1};
        cardinalities[fieldId].computeIfPresent(termId, (key, idCounts) -> {
            result[0] = idCounts.get(id);
            return idCounts;
        });
        if (result[0] >= 0) {
            return result[0];
        } else {
            return backingFieldIndex.getCardinality(fieldId, termId, id, primitiveBuffer);
        }
    }

    @Override
    public long[] getCardinalities(int fieldId, MiruTermId termId, int[] ids, byte[] primitiveBuffer) throws Exception {
        long[] counts = new long[ids.length];
        if (cardinalities[fieldId] != null) {
            int[] consumableIds = Arrays.copyOf(ids, ids.length);
            int[] consumed = new int[1];
            cardinalities[fieldId].compute(termId, (key, idCounts) -> {
                if (idCounts != null) {
                    for (int i = 0; i < consumableIds.length; i++) {
                        long count = idCounts.get(consumableIds[i]);
                        counts[i] = count;
                        if (count >= 0) {
                            consumableIds[i] = -1;
                            consumed[0]++;
                        }
                    }
                }
                return idCounts;
            });
            if (consumed[0] < ids.length) {
                long[] existing = backingFieldIndex.getCardinalities(fieldId, termId, consumableIds, primitiveBuffer);
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] < 0) {
                        counts[i] = existing[i];
                    }
                }
            }
        } else {
            Arrays.fill(counts, -1);
        }
        return counts;
    }

    @Override
    public long getGlobalCardinality(int fieldId, MiruTermId termId, byte[] primitiveBuffer) throws Exception {
        if (cardinalities[fieldId] != null) {
            long[] count = {-1};
            cardinalities[fieldId].compute(termId, (key, idCounts) -> {
                if (idCounts != null) {
                    count[0] = idCounts.get(-1);
                }
                return idCounts;
            });
            if (count[0] >= 0) {
                return count[0];
            } else {
                return backingFieldIndex.getGlobalCardinality(fieldId, termId, primitiveBuffer);
            }
        }
        return -1;
    }

    @Override
    public void mergeCardinalities(int fieldId, MiruTermId termId, int[] ids, long[] counts, byte[] primitiveBuffer) throws Exception {
        putCardinalities(fieldId, termId, ids, counts, false, primitiveBuffer);
    }

    private void putCardinalities(int fieldId, MiruTermId termId, int[] ids, long[] counts, boolean append, byte[] primitiveBuffer) throws Exception {
        if (cardinalities[fieldId] != null && counts != null) {
            long[] backingCounts = append ? null : backingFieldIndex.getCardinalities(fieldId, termId, ids, primitiveBuffer);

            cardinalities[fieldId].compute(termId, (key, idCounts) -> {
                if (idCounts == null) {
                    idCounts = new TIntLongHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1);
                }

                long delta = 0;
                for (int i = 0; i < ids.length; i++) {
                    long existing = idCounts.put(ids[i], counts[i]);
                    if (existing >= 0) {
                        delta = counts[i] - existing;
                    } else if (backingCounts != null && backingCounts[i] > 0) {
                        delta = counts[i] - backingCounts[i];
                    } else {
                        delta = counts[i];
                    }
                }

                if (!idCounts.adjustValue(-1, delta)) {
                    try {
                        long globalCount = backingFieldIndex.getGlobalCardinality(fieldId, termId, primitiveBuffer);
                        idCounts.put(-1, globalCount > 0 ? delta + globalCount : delta);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                return idCounts;
            });
        }
    }

    @Override
    public void merge(byte[] primitiveBuffer) throws Exception {
        for (int fieldId = 0; fieldId < fieldIndexDeltas.length; fieldId++) {
            ConcurrentMap<MiruTermId, MiruDeltaInvertedIndex.Delta<BM>> deltaMap = fieldIndexDeltas[fieldId];
            for (Map.Entry<MiruTermId, MiruDeltaInvertedIndex.Delta<BM>> entry : deltaMap.entrySet()) {
                MiruDeltaInvertedIndex.Delta<BM> delta = entry.getValue();
                MiruDeltaInvertedIndex<BM> invertedIndex = new MiruDeltaInvertedIndex<>(bitmaps,
                    backingFieldIndex.getOrCreateInvertedIndex(fieldId, entry.getKey()),
                    delta,
                    getIndexKey(fieldId, entry.getKey()),
                    fieldIndexCache, versionCache);
                invertedIndex.merge(primitiveBuffer);
            }
            deltaMap.clear();

            ConcurrentHashMap<MiruTermId, TIntLongMap> cardinality = cardinalities[fieldId];
            if (cardinality != null) {
                for (Map.Entry<MiruTermId, TIntLongMap> entry : cardinality.entrySet()) {
                    MiruTermId termId = entry.getKey();
                    TIntLongMap idCounts = entry.getValue();
                    int[] ids = new int[idCounts.size() - 1];
                    long[] counts = new long[idCounts.size() - 1];
                    int i = 0;
                    TIntLongIterator iter = idCounts.iterator();
                    while (iter.hasNext()) {
                        iter.advance();
                        int id = iter.key();
                        if (id >= 0) {
                            ids[i] = id;
                            counts[i] = iter.value();
                            i++;
                        }
                    }
                    backingFieldIndex.mergeCardinalities(fieldId, termId, ids, counts, primitiveBuffer);
                }
                cardinality.clear();
            }
        }
    }
}
