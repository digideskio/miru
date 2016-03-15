package com.jivesoftware.os.miru.service.index.delta;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.index.MiruActivityAndId;
import com.jivesoftware.os.miru.plugin.index.MiruActivityIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInternalActivity;
import com.jivesoftware.os.miru.service.index.Mergeable;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * DELTA FORCE
 */
public class MiruDeltaActivityIndex implements MiruActivityIndex, Mergeable {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruActivityIndex backingIndex;
    private final Map<Integer, MiruActivityAndId<MiruInternalActivity>> activities = Maps.newConcurrentMap();
    private final AtomicInteger lastId = new AtomicInteger(-1);

    public MiruDeltaActivityIndex(MiruActivityIndex backingIndex) {
        this.backingIndex = backingIndex;
    }

    @Override
    public MiruInternalActivity get(String name, MiruTenantId tenantId, int index, StackBuffer stackBuffer) throws IOException, InterruptedException {
        //TODO consider writing through to the backing index for old indexes to avoid the double lookup
        MiruActivityAndId<MiruInternalActivity> activityAndId = activities.get(index);
        if (activityAndId != null) {
            return activityAndId.activity;
        } else {
            return backingIndex.get(name, tenantId, index, stackBuffer);
        }
    }

    @Override
    public MiruTermId[] get(String name, int index, int fieldId, StackBuffer stackBuffer) throws IOException, InterruptedException {
        MiruActivityAndId<MiruInternalActivity> activityAndId = activities.get(index);
        if (activityAndId != null) {
            return activityAndId.activity.fieldsValues[fieldId];
        } else {
            return backingIndex.get(name, index, fieldId, stackBuffer);
        }
    }

    @Override
    public MiruTermId[][] getAll(String name, int[] indexes, int fieldId, StackBuffer stackBuffer) throws IOException, InterruptedException {
        return getAll(name, indexes, 0, indexes.length, fieldId, stackBuffer);
    }

    @Override
    public MiruTermId[][] getAll(String name,
        int[] indexes,
        int offset,
        int length,
        int fieldId,
        StackBuffer stackBuffer) throws IOException, InterruptedException {

        MiruTermId[][] allTermIds = new MiruTermId[length][];
        boolean missed = false;
        for (int i = 0; i < length; i++) {
            if (indexes[offset + i] > -1) {
                MiruActivityAndId<MiruInternalActivity> activityAndId = activities.get(indexes[offset + i]);
                if (activityAndId != null) {
                    indexes[offset + i] = -1;
                    allTermIds[i] = activityAndId.activity.fieldsValues[fieldId];
                } else {
                    missed = true;
                }
            }
        }
        if (missed) {
            MiruTermId[][] backingTermIds = backingIndex.getAll(name, indexes, offset, length, fieldId, stackBuffer);
            for (int i = 0; i < length; i++) {
                if (backingTermIds[i] != null) {
                    allTermIds[i] = backingTermIds[i];
                }
            }
        }
        return allTermIds;
    }

    @Override
    public int lastId(StackBuffer stackBuffer) {
        int id = lastId.get();
        if (id < 0) {
            return backingIndex.lastId(stackBuffer);
        } else {
            return id;
        }
    }

    @Override
    public void setAndReady(MiruSchema schema, Collection<MiruActivityAndId<MiruInternalActivity>> activityAndIds, StackBuffer stackBuffer) throws Exception {
        if (!activityAndIds.isEmpty()) {
            int lastIndex = setInternal(activityAndIds);
            ready(lastIndex, stackBuffer);
        }
    }

    @Override
    public void set(MiruSchema schema, Collection<MiruActivityAndId<MiruInternalActivity>> activityAndIds, StackBuffer stackBuffer) {
        if (!activityAndIds.isEmpty()) {
            setInternal(activityAndIds);
        }
    }

    private int setInternal(Collection<MiruActivityAndId<MiruInternalActivity>> activityAndIds) {
        int lastIndex = -1;
        for (MiruActivityAndId<MiruInternalActivity> activityAndId : activityAndIds) {
            checkArgument(activityAndId.id >= 0, "Index parameter is out of bounds. The value %s must be >=0", activityAndId.id);
            activities.put(activityAndId.id, activityAndId);
            lastIndex = Math.max(lastIndex, activityAndId.id);
        }
        return lastIndex;
    }

    @Override
    public void ready(int index, StackBuffer stackBuffer) throws Exception {
        lastId.updateAndGet(existing -> {
            if (existing >= index) {
                LOG.warn("Attempted to ready id {} but already readied id {}", index, existing);
                return existing;
            } else {
                return index;
            }
        });
    }

    @Override
    public void close() {
        backingIndex.close();
    }

    @Override
    public void merge(MiruSchema schema, StackBuffer stackBuffer) throws Exception {
        backingIndex.setAndReady(schema, activities.values(), stackBuffer);
        activities.clear();
    }
}
