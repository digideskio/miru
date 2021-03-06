package com.jivesoftware.os.miru.sync.deployable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jivesoftware.os.amza.api.PartitionClient;
import com.jivesoftware.os.amza.api.PartitionClientProvider;
import com.jivesoftware.os.amza.api.RingPartitionProperties;
import com.jivesoftware.os.amza.api.partition.PartitionName;
import com.jivesoftware.os.amza.api.partition.PartitionProperties;
import com.jivesoftware.os.amza.api.ring.RingMember;
import com.jivesoftware.os.amza.api.wal.KeyUtil;
import com.jivesoftware.os.amza.client.aquarium.AmzaClientAquariumProvider;
import com.jivesoftware.os.amza.client.test.InMemoryPartitionClient;
import com.jivesoftware.os.aquarium.AquariumStats;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.jive.utils.ordered.id.TimestampedOrderIdProvider;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruActivity;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivityFactory;
import com.jivesoftware.os.miru.api.activity.MiruReadEvent;
import com.jivesoftware.os.miru.api.activity.TimeAndVersion;
import com.jivesoftware.os.miru.api.activity.schema.DefaultMiruSchemaDefinition;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchemaProvider;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.sync.MiruSyncClient;
import com.jivesoftware.os.miru.api.topology.NamedCursor;
import com.jivesoftware.os.miru.api.wal.AmzaCursor;
import com.jivesoftware.os.miru.api.wal.AmzaSipCursor;
import com.jivesoftware.os.miru.api.wal.MiruActivityWALStatus;
import com.jivesoftware.os.miru.api.wal.MiruVersionedActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient;
import com.jivesoftware.os.miru.api.wal.MiruWALEntry;
import com.jivesoftware.os.miru.sync.api.MiruSyncSenderConfig;
import com.jivesoftware.os.miru.sync.api.MiruSyncTenantConfig;
import com.jivesoftware.os.miru.sync.api.MiruSyncTenantTuple;
import com.jivesoftware.os.miru.sync.api.MiruSyncTimeShiftStrategy;
import com.jivesoftware.os.miru.sync.deployable.MiruSyncSender.ProgressType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.commons.lang.mutable.MutableLong;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.jivesoftware.os.miru.sync.deployable.MiruSyncSender.ProgressType.forward;
import static com.jivesoftware.os.miru.sync.deployable.MiruSyncSender.ProgressType.initial;
import static com.jivesoftware.os.miru.sync.deployable.MiruSyncSender.ProgressType.reverse;

/**
 *
 */
public class MiruSyncSenderTest {

    @Test
    public void testProgress() throws Exception {
        MiruTenantId tenantId = new MiruTenantId("tenant1".getBytes(StandardCharsets.UTF_8));
        RingMember ringMember = new RingMember("member1");

        SnowflakeIdPacker idPacker = new SnowflakeIdPacker();
        TimestampedOrderIdProvider orderIdProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(1),
            idPacker,
            new JiveEpochTimestampProvider());
        PartitionClientProvider partitionClientProvider = new InMemoryPartitionClientProvider(ringMember, orderIdProvider);

        AmzaClientAquariumProvider amzaClientAquariumProvider = new AmzaClientAquariumProvider(new AquariumStats(),
            "test",
            partitionClientProvider,
            orderIdProvider,
            ringMember.asAquariumMember(),
            count -> count == 1,
            () -> Sets.newHashSet(ringMember.asAquariumMember()),
            128,
            128,
            5_000L,
            100L,
            60_000L,
            10_000L,
            Executors.newSingleThreadExecutor(),
            100L,
            1_000L,
            10_000L,
            false);

        AtomicInteger largestPartitionId = new AtomicInteger(10);
        int initialId = largestPartitionId.get();

        int[] reverseSyncedActivity = new int[1];
        int[] reverseSyncedBoundary = new int[1];
        int[] forwardSyncedActivity = new int[1];
        int[] forwardSyncedBoundary = new int[1];
        MiruSyncClient syncClient = new MiruSyncClient() {
            @Override
            public void writeActivity(MiruTenantId tenantId,
                MiruPartitionId partitionId,
                List<MiruPartitionedActivity> partitionedActivities) throws Exception {
                for (MiruPartitionedActivity partitionedActivity : partitionedActivities) {
                    if (partitionId.getId() < initialId) {
                        if (partitionedActivity.type.isActivityType()) {
                            reverseSyncedActivity[0]++;
                        } else if (partitionedActivity.type.isBoundaryType()) {
                            reverseSyncedBoundary[0]++;
                        }
                    } else {
                        if (partitionedActivity.type.isActivityType()) {
                            forwardSyncedActivity[0]++;
                        } else if (partitionedActivity.type.isBoundaryType()) {
                            forwardSyncedBoundary[0]++;
                        }
                    }
                }
            }

            @Override
            public void registerSchema(MiruTenantId tenantId, MiruSchema schema) throws Exception {
            }
        };

        MiruSchema schema = new MiruSchema.Builder("test", 1)
            .setFieldDefinitions(DefaultMiruSchemaDefinition.FIELDS)
            .build();
        MiruSchemaProvider schemaProvider = miruTenantId -> schema;

        TestWALClient testWALClient = new TestWALClient(tenantId, largestPartitionId);
        MiruSyncSender<AmzaCursor, AmzaSipCursor> syncService = new MiruSyncSender<>(
            new MiruStats(),
            new MiruSyncSenderConfig(
                "default",
                true,
                100L,
                0L,
                1_000,
                false,
                "",
                "",
                -1,
                "",
                "",
                "",
                true),
            amzaClientAquariumProvider,
            orderIdProvider,
            idPacker, 1,
            Executors.newScheduledThreadPool(1),
            schemaProvider,
            new NoOpClusterClient(),
            testWALClient,
            syncClient,
            partitionClientProvider,
            new ObjectMapper(),
            senderName -> ImmutableMap.of(new MiruSyncTenantTuple(tenantId, tenantId), new MiruSyncTenantConfig(
                -1,
                Long.MAX_VALUE,
                0,
                0,
                0,
                MiruSyncTimeShiftStrategy.none,
                false)),
            null,
            AmzaCursor.class);

        amzaClientAquariumProvider.start();
        syncService.start();

        long failAfter = System.currentTimeMillis() + 60_000L;
        int[] progressIds = awaitProgress(tenantId, syncService, reverse, -1, failAfter);

        Assert.assertEquals(progressIds[initial.index], initialId);
        Assert.assertEquals(progressIds[reverse.index], -1);
        Assert.assertEquals(progressIds[forward.index], initialId);
        Assert.assertEquals(reverseSyncedActivity[0], largestPartitionId.get(),
            "Should reverse sync 1 activity each for partitions less than " + largestPartitionId.get());
        Assert.assertEquals(reverseSyncedBoundary[0], 3 * largestPartitionId.get(),
            "Should reverse sync 2 boundaries each for partitions less than " + largestPartitionId.get());
        Assert.assertEquals(forwardSyncedActivity[0], 0, "Should not forward sync any activity yet");
        Assert.assertEquals(forwardSyncedBoundary[0], 1, "Should forward sync 1 boundary");

        reverseSyncedActivity[0] = 0;
        reverseSyncedBoundary[0] = 0;
        largestPartitionId.addAndGet(10);

        progressIds = awaitProgress(tenantId, syncService, forward, largestPartitionId.get(), failAfter);

        Assert.assertEquals(progressIds[initial.index], initialId);
        Assert.assertEquals(progressIds[reverse.index], -1);
        Assert.assertEquals(progressIds[forward.index], largestPartitionId.get());
        Assert.assertEquals(reverseSyncedActivity[0], 0, "Should not reverse sync any additional activity yet");
        Assert.assertEquals(reverseSyncedBoundary[0], 0, "Should not reverse sync any additional boundary yet");
        Assert.assertEquals(forwardSyncedActivity[0], largestPartitionId.get() - initialId,
            "Should forward sync 1 activity each for partitions from " + initialId + " to " + largestPartitionId.get());
        Assert.assertEquals(forwardSyncedActivity[0], largestPartitionId.get() - initialId,
            "Should forward sync 1 boundary each for partitions from " + initialId + " to " + largestPartitionId.get());
    }

    private int[] awaitProgress(MiruTenantId tenantId,
        MiruSyncSender<AmzaCursor, AmzaSipCursor> syncService,
        ProgressType awaitType,
        int awaitValue,
        long failAfter) throws Exception {
        int[] progressIds = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        while (true) {
            syncService.streamProgress(tenantId, tenantId, (fromTenantId, toTenantId, type, partitionId, timestamp, taking) -> {
                Assert.assertEquals(toTenantId, tenantId);
                progressIds[type.index] = partitionId;
                return true;
            });
            if (progressIds[awaitType.index] == awaitValue) {
                break;
            }
            if (System.currentTimeMillis() > failAfter) {
                Assert.fail("Timed out awaiting progress");
            }
            Thread.sleep(100L);
        }
        return progressIds;
    }

    private static class InMemoryPartitionClientProvider implements PartitionClientProvider {

        private final RingMember ringMember;
        private final OrderIdProvider orderIdProvider;

        private final Map<PartitionName, PartitionClient> clients = Maps.newConcurrentMap();

        public InMemoryPartitionClientProvider(RingMember ringMember, OrderIdProvider orderIdProvider) {
            this.ringMember = ringMember;
            this.orderIdProvider = orderIdProvider;
        }

        @Override
        public RingPartitionProperties getProperties(PartitionName partitionName) throws Exception {
            return null;
        }

        @Override
        public PartitionClient getPartition(PartitionName partitionName) throws Exception {
            return clients.computeIfAbsent(partitionName,
                partitionName1 -> new InMemoryPartitionClient(ringMember,
                    new ConcurrentSkipListMap<>(),
                    new ConcurrentSkipListMap<>(KeyUtil.lexicographicalComparator()),
                    orderIdProvider));
        }

        @Override
        public PartitionClient getPartition(PartitionName partitionName, int ringSize, PartitionProperties partitionProperties) throws Exception {
            return getPartition(partitionName);
        }
    }

    private static class TestWALClient implements MiruWALClient<AmzaCursor, AmzaSipCursor> {

        private final MiruTenantId testTenantId;
        private final AtomicInteger largestPartitionId;

        private final AtomicLong timestamper = new AtomicLong(1_000);
        private final MiruPartitionedActivityFactory partitionedActivityFactory = new MiruPartitionedActivityFactory(timestamper::incrementAndGet);
        private final NamedCursor continueCursor = new NamedCursor("test", 1L);
        private final NamedCursor stopCursor = new NamedCursor("test", 2L);

        private TestWALClient(MiruTenantId testTenantId, AtomicInteger largestPartitionId) {
            this.testTenantId = testTenantId;
            this.largestPartitionId = largestPartitionId;
        }

        @Override
        public List<MiruTenantId> getAllTenantIds() throws Exception {
            return Collections.singletonList(testTenantId);
        }

        @Override
        public MiruPartitionId getLargestPartitionId(MiruTenantId tenantId) throws Exception {
            return MiruPartitionId.of(largestPartitionId.get());
        }

        @Override
        public MiruActivityWALStatus getActivityWALStatusForTenant(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
            Preconditions.checkArgument(tenantId.equals(testTenantId));
            List<Integer> begins = Collections.singletonList(1);
            List<Integer> ends = (partitionId.getId() < largestPartitionId.get()) ? begins : Collections.emptyList();
            return new MiruActivityWALStatus(partitionId, Collections.emptyList(), begins, ends);
        }

        @Override
        public long getActivityCount(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
            if (partitionId.getId() == largestPartitionId.get()) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public StreamBatch<MiruWALEntry, AmzaCursor> getActivity(MiruTenantId tenantId,
            MiruPartitionId partitionId,
            AmzaCursor cursor,
            int batchSize,
            long stopAtTimestamp,
            MutableLong bytesCount) throws Exception {
            List<MiruWALEntry> entries;
            NamedCursor namedCursor = (cursor == null || cursor.cursors == null || cursor.cursors.isEmpty()) ? null : cursor.cursors.get(0);
            if (partitionId.getId() == largestPartitionId.get() || namedCursor != null && namedCursor.compareTo(stopCursor) == 0) {
                entries = Collections.emptyList();
            } else {
                entries = Collections.singletonList(
                    new MiruWALEntry(1L,
                        2L,
                        partitionedActivityFactory.activity(1,
                            partitionId,
                            0,
                            new MiruActivity(tenantId, 1L, 2L, false, null, Collections.emptyMap(), Collections.emptyMap()))));
            }
            return new StreamBatch<>(
                entries,
                new AmzaCursor(Collections.singletonList(entries.isEmpty() ? continueCursor : stopCursor),
                    new AmzaSipCursor(Collections.emptyList(), true)),
                true,
                Collections.emptySet());
        }

        @Override
        public void writeActivity(MiruTenantId tenantId, MiruPartitionId partitionId, List<MiruPartitionedActivity> partitionedActivities) throws Exception {

        }

        @Override
        public void writeReadTracking(MiruTenantId tenantId,
            List<MiruReadEvent> readEvents,
            Function<MiruReadEvent, MiruPartitionedActivity> transformer) throws Exception {

        }

        @Override
        public WriterCursor getCursorForWriterId(MiruTenantId tenantId, MiruPartitionId partitionId, int writerId) throws Exception {
            return null;
        }

        @Override
        public long oldestActivityClockTimestamp(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
            return 0;
        }

        @Override
        public List<MiruVersionedActivityLookupEntry> getVersionedEntries(MiruTenantId tenantId,
            MiruPartitionId partitionId,
            Long[] timestamps) throws Exception {
            return null;
        }

        @Override
        public StreamBatch<MiruWALEntry, AmzaSipCursor> sipActivity(MiruTenantId tenantId,
            MiruPartitionId partitionId,
            AmzaSipCursor cursor,
            Set<TimeAndVersion> lastSeen,
            int batchSize) throws Exception {
            return null;
        }

        @Override
        public OldestReadResult<AmzaSipCursor> oldestReadEventId(MiruTenantId tenantId,
            MiruStreamId streamId,
            AmzaSipCursor cursor,
            boolean createIfAbsent) throws Exception {
            return null;
        }

        @Override
        public StreamBatch<MiruWALEntry, Long> scanRead(MiruTenantId tenantId,
            MiruStreamId streamId,
            long fromTimestamp,
            int batchSize,
            boolean createIfAbsent) throws Exception {
            return null;
        }
    }
}