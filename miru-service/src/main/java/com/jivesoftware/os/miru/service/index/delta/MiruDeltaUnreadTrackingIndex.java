package com.jivesoftware.os.miru.service.index.delta;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndexAppender;
import com.jivesoftware.os.miru.plugin.index.MiruUnreadTrackingIndex;
import com.jivesoftware.os.miru.service.index.Mergeable;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;

/** @author jonathan */
public class MiruDeltaUnreadTrackingIndex<BM> implements MiruUnreadTrackingIndex<BM>, Mergeable {

    private final MiruBitmaps<BM> bitmaps;
    private final long indexId;
    private final MiruUnreadTrackingIndex<BM> backingIndex;
    private final Cache<MiruFieldIndex.IndexKey, Optional<?>> fieldIndexCache;
    private final ConcurrentMap<MiruStreamId, MiruDeltaInvertedIndex<BM>> unreadDeltas = Maps.newConcurrentMap();

    public MiruDeltaUnreadTrackingIndex(MiruBitmaps<BM> bitmaps,
        long indexId,
        MiruUnreadTrackingIndex<BM> backingIndex,
        Cache<MiruFieldIndex.IndexKey, Optional<?>> fieldIndexCache) {
        this.bitmaps = bitmaps;
        this.indexId = indexId;
        this.backingIndex = backingIndex;
        this.fieldIndexCache = fieldIndexCache;
    }

    @Override
    public void append(MiruStreamId streamId, byte[] primitiveBuffer, int... ids) throws Exception {
        getAppender(streamId).append(primitiveBuffer, ids);
    }

    @Override
    public MiruInvertedIndex<BM> getUnread(MiruStreamId streamId) throws Exception {
        MiruDeltaInvertedIndex<BM> delta = unreadDeltas.get(streamId);
        if (delta == null) {
            delta = new MiruDeltaInvertedIndex<>(bitmaps, backingIndex.getUnread(streamId), new MiruDeltaInvertedIndex.Delta<BM>(),
                new MiruFieldIndex.IndexKey(indexId, streamId.getBytes()), fieldIndexCache, null);
            MiruDeltaInvertedIndex<BM> existing = unreadDeltas.putIfAbsent(streamId, delta);
            if (existing != null) {
                delta = existing;
            }
        }
        return delta;
    }

    @Override
    public MiruInvertedIndexAppender getAppender(MiruStreamId streamId) throws Exception {
        return getUnread(streamId);
    }

    @Override
    public void applyRead(MiruStreamId streamId, BM readMask, byte[] primitiveBuffer) throws Exception {
        MiruInvertedIndex<BM> unread = getUnread(streamId);
        unread.andNotToSourceSize(Collections.singletonList(readMask), primitiveBuffer);
    }

    @Override
    public void applyUnread(MiruStreamId streamId, BM unreadMask, byte[] primitiveBuffer) throws Exception {
        MiruInvertedIndex<BM> unread = getUnread(streamId);
        unread.orToSourceSize(unreadMask, primitiveBuffer);
    }

    @Override
    public void close() {
        backingIndex.close();
    }

    @Override
    public void merge(byte[] primitiveBuffer) throws Exception {
        for (MiruDeltaInvertedIndex<BM> delta : unreadDeltas.values()) {
            delta.merge(primitiveBuffer);
        }
        unreadDeltas.clear();
    }
}
