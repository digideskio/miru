package com.jivesoftware.os.miru.service.index.memory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.MiruInboxIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndexAppender;
import com.jivesoftware.os.miru.service.index.BulkExport;
import com.jivesoftware.os.miru.service.index.BulkImport;
import com.jivesoftware.os.miru.service.index.memory.MiruInMemoryInboxIndex.InboxAndLastActivityIndex;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory representation of all inbox indexes in a given system
 */
public class MiruInMemoryInboxIndex<BM> implements MiruInboxIndex<BM>, BulkImport<InboxAndLastActivityIndex<BM>>, BulkExport<InboxAndLastActivityIndex<BM>> {

    private final MiruBitmaps<BM> bitmaps;
    private final ConcurrentMap<MiruStreamId, MiruInvertedIndex<BM>> index;
    private final Map<MiruStreamId, Integer> lastActivityIndex;

    public MiruInMemoryInboxIndex(MiruBitmaps<BM> bitmaps) {
        this.bitmaps = bitmaps;
        this.index = new ConcurrentHashMap<>();
        this.lastActivityIndex = new ConcurrentHashMap<>();
    }

    @Override
    public void index(MiruStreamId streamId, int id) throws Exception {
        MiruInvertedIndexAppender inbox = getAppender(streamId);
        inbox.append(id);
    }

    @Override
    public Optional<BM> getInbox(MiruStreamId streamId) throws Exception {
        MiruInvertedIndex<BM> got = index.get(streamId);
        if (got == null) {
            return Optional.absent();
        }
        return Optional.<BM>fromNullable(got.getIndex());
    }

    @Override
    public MiruInvertedIndexAppender getAppender(MiruStreamId streamId) throws Exception {
        MiruInvertedIndex<BM> got = index.get(streamId);
        if (got == null) {
            index.putIfAbsent(streamId, new MiruInMemoryInvertedIndex<>(bitmaps));
            got = index.get(streamId);
        }
        return got;
    }

    @Override
    public int getLastActivityIndex(MiruStreamId streamId) {
        Integer got = lastActivityIndex.get(streamId);
        if (got == null) {
            got = -1;
        }
        return got;
    }

    @Override
    public void setLastActivityIndex(MiruStreamId streamId, int activityIndex) {
        lastActivityIndex.put(streamId, activityIndex);
    }

    @Override
    public long sizeInMemory() throws Exception {
        long sizeInBytes = lastActivityIndex.size() * 12; // 8 byte stream ID + 4 byte index
        for (Map.Entry<MiruStreamId, MiruInvertedIndex<BM>> entry : index.entrySet()) {
            sizeInBytes += entry.getKey().getBytes().length + entry.getValue().sizeInMemory();
        }
        return sizeInBytes;
    }

    @Override
    public long sizeOnDisk() throws Exception {
        long sizeInBytes = 0;
        for (Map.Entry<MiruStreamId, MiruInvertedIndex<BM>> entry : index.entrySet()) {
            sizeInBytes += entry.getValue().sizeOnDisk();
        }
        return sizeInBytes;
    }

    @Override
    public void close() {
    }

    @Override
    public InboxAndLastActivityIndex<BM> bulkExport(MiruTenantId tenantId) throws Exception {
        return new InboxAndLastActivityIndex<>(index, lastActivityIndex);
    }

    @Override
    public void bulkImport(MiruTenantId tenantId, BulkExport<InboxAndLastActivityIndex<BM>> importItems) throws Exception {
        InboxAndLastActivityIndex<BM> inboxAndLastActivityIndex = importItems.bulkExport(tenantId);
        this.index.putAll(inboxAndLastActivityIndex.index);
        this.lastActivityIndex.putAll(inboxAndLastActivityIndex.lastActivityIndex);
    }

    public static class InboxAndLastActivityIndex<BM2> {
        public final Map<MiruStreamId, MiruInvertedIndex<BM2>> index;
        public final Map<MiruStreamId, Integer> lastActivityIndex;

        public InboxAndLastActivityIndex(Map<MiruStreamId, MiruInvertedIndex<BM2>> index, Map<MiruStreamId, Integer> lastActivityIndex) {
            this.index = ImmutableMap.copyOf(index);
            this.lastActivityIndex = ImmutableMap.copyOf(lastActivityIndex);
        }
    }
}
