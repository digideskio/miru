package com.jivesoftware.os.miru.wal.deployable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.jivesoftware.os.amza.berkeleydb.BerkeleyDBWALIndexProvider;
import com.jivesoftware.os.amza.client.AmzaClientProvider;
import com.jivesoftware.os.amza.service.AmzaService;
import com.jivesoftware.os.amza.service.AmzaServiceInitializer.AmzaServiceConfig;
import com.jivesoftware.os.amza.service.EmbeddedAmzaServiceInitializer;
import com.jivesoftware.os.amza.service.WALIndexProviderRegistry;
import com.jivesoftware.os.amza.service.replication.TakeFailureListener;
import com.jivesoftware.os.amza.service.storage.PartitionPropertyMarshaller;
import com.jivesoftware.os.amza.shared.partition.PartitionProperties;
import com.jivesoftware.os.amza.shared.ring.RingHost;
import com.jivesoftware.os.amza.shared.ring.RingMember;
import com.jivesoftware.os.amza.shared.stats.AmzaStats;
import com.jivesoftware.os.amza.shared.take.AvailableRowsTaker;
import com.jivesoftware.os.amza.shared.take.RowsTakerFactory;
import com.jivesoftware.os.amza.transport.http.replication.HttpAvailableRowsTaker;
import com.jivesoftware.os.amza.transport.http.replication.HttpRowsTaker;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.jive.utils.ordered.id.TimestampedOrderIdProvider;
import com.jivesoftware.os.miru.api.HostPortProvider;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.marshall.JacksonJsonObjectTypeMarshaller;
import com.jivesoftware.os.miru.api.marshall.MiruVoidByte;
import com.jivesoftware.os.miru.api.wal.RCVSCursor;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryClusterClient;
import com.jivesoftware.os.miru.cluster.MiruReplicaSetDirector;
import com.jivesoftware.os.miru.cluster.amza.AmzaClusterRegistry;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer.MiruSoyRendererConfig;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.miru.wal.RCVSWALInitializer;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALWriter;
import com.jivesoftware.os.miru.wal.lookup.MiruWALLookup;
import com.jivesoftware.os.miru.wal.lookup.RCVSWALLookup;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALWriter;
import com.jivesoftware.os.rcvs.inmemory.InMemoryRowColumnValueStoreInitializer;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptorsProvider;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptorsRequest;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptorsResponse;
import com.jivesoftware.os.routing.bird.shared.TenantRoutingProvider;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.merlin.config.BindInterfaceToConfiguration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author jonathan.colt
 */
public class MiruWALUIServiceNGTest {

    private MiruWALUIService service;
    private MiruTenantId tenantId;
    private MiruPartitionId partitionId;

    @BeforeClass
    public void before() throws Exception {
        tenantId = new MiruTenantId("test1".getBytes());
        partitionId = MiruPartitionId.of(0);

        MiruSoyRendererConfig config = BindInterfaceToConfiguration.bindDefault(MiruSoyRendererConfig.class);
        config.setPathToSoyResources("src/main/home/resources/soy");

        InMemoryRowColumnValueStoreInitializer rowColumnValueStoreInitializer = new InMemoryRowColumnValueStoreInitializer();
        ObjectMapper mapper = new ObjectMapper();

        RCVSWALInitializer.RCVSWAL wal = new RCVSWALInitializer().initialize("test", rowColumnValueStoreInitializer, mapper);
        wal.getWALLookupTable().add(MiruVoidByte.INSTANCE, tenantId, partitionId, System.currentTimeMillis(), null, null);
        wal.getWriterPartitionRegistry().add(MiruVoidByte.INSTANCE, tenantId, 1, MiruPartitionId.of(0), null, null);

        HostPortProvider hostPortProvider = host -> 10_000;

        RCVSActivityWALWriter activityWALWriter = new RCVSActivityWALWriter(wal.getActivityWAL(), wal.getActivitySipWAL());
        RCVSActivityWALReader activityWALReader = new RCVSActivityWALReader(hostPortProvider, wal.getActivityWAL(), wal.getActivitySipWAL());
        RCVSReadTrackingWALWriter readTrackingWALWriter = new RCVSReadTrackingWALWriter(wal.getReadTrackingWAL(), wal.getReadTrackingSipWAL());
        RCVSReadTrackingWALReader readTrackingWALReader = new RCVSReadTrackingWALReader(hostPortProvider,
            wal.getReadTrackingWAL(),
            wal.getReadTrackingSipWAL());
        MiruWALLookup walLookup = new RCVSWALLookup(wal.getWALLookupTable());

        File amzaDataDir = Files.createTempDir();
        File amzaIndexDir = Files.createTempDir();

        RingMember ringMember = new RingMember("testInstance");
        RingHost ringHost = new RingHost("localhost", 10000);
        SnowflakeIdPacker idPacker = new SnowflakeIdPacker();
        TimestampedOrderIdProvider orderIdProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(1),
            idPacker,
            new JiveEpochTimestampProvider());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);

        WALIndexProviderRegistry indexProviderRegistry = new WALIndexProviderRegistry();
        String[] walIndexDirs = new String[]{amzaIndexDir.getAbsolutePath()};
        indexProviderRegistry.register("berkeleydb", new BerkeleyDBWALIndexProvider(walIndexDirs, walIndexDirs.length));

        AmzaStats amzaStats = new AmzaStats();
        AvailableRowsTaker availableRowsTaker = new HttpAvailableRowsTaker(amzaStats);
        RowsTakerFactory rowsTakerFactory = () -> new HttpRowsTaker(amzaStats);

        final AmzaServiceConfig amzaServiceConfig = new AmzaServiceConfig();
        amzaServiceConfig.workingDirectories = new String[]{amzaDataDir.getAbsolutePath()};
        amzaServiceConfig.numberOfDeltaStripes = amzaServiceConfig.workingDirectories.length;
        amzaServiceConfig.numberOfTakerThreads = 1;

        PartitionPropertyMarshaller partitionPropertyMarshaller = new PartitionPropertyMarshaller() {

            @Override
            public PartitionProperties fromBytes(byte[] bytes) {
                try {
                    return mapper.readValue(bytes, PartitionProperties.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public byte[] toBytes(PartitionProperties partitionProperties) {
                try {
                    return mapper.writeValueAsBytes(partitionProperties);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        AmzaService amzaService = new EmbeddedAmzaServiceInitializer().initialize(amzaServiceConfig,
            amzaStats,
            ringMember,
            ringHost,
            orderIdProvider,
            idPacker,
            partitionPropertyMarshaller,
            indexProviderRegistry,
            availableRowsTaker,
            rowsTakerFactory,
            Optional.<TakeFailureListener>absent(),
            rowsChanged -> {
            });

        amzaService.start();

        AmzaClientProvider amzaClientProvider = new AmzaClientProvider(amzaService);
        MiruClusterRegistry clusterRegistry = new AmzaClusterRegistry(amzaService,
            amzaClientProvider,
            0,
            10_000L,
            new JacksonJsonObjectTypeMarshaller<>(MiruSchema.class, mapper),
            3,
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.DAYS.toMillis(365),
            0);
        MiruRegistryClusterClient clusterClient = new MiruRegistryClusterClient(clusterRegistry, new MiruReplicaSetDirector(orderIdProvider, clusterRegistry));
        MiruWALDirector<RCVSCursor, RCVSSipCursor> director = new MiruWALDirector<>(walLookup,
            activityWALReader,
            activityWALWriter,
            readTrackingWALReader,
            readTrackingWALWriter,
            clusterClient);

        MiruSoyRenderer renderer = new MiruSoyRendererInitializer().initialize(config);
        service = new MiruWriterUIServiceInitializer().initialize("cluster", 1, renderer, new TenantRoutingProvider("1", new ConnectionDescriptorsProvider() {
            @Override
            public ConnectionDescriptorsResponse requestConnections(ConnectionDescriptorsRequest cdr) {
                return null;
            }
        }), director, null, activityWALReader, new MiruStats());
    }

    @Test
    public void testRenderActivityWALWithTenant() throws Exception {
        String rendered = service.renderActivityWALWithTenant(tenantId);
        assertTrue(rendered.contains(tenantId.toString()));
        assertTrue(rendered.contains("/miru/wal/activity/" + tenantId + "/" + partitionId + "#focus"));
    }

}
