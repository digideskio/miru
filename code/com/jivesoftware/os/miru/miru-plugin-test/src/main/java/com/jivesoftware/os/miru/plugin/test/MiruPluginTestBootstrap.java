package com.jivesoftware.os.miru.plugin.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Interners;
import com.jivesoftware.os.jive.utils.health.api.HealthCheckConfigBinder;
import com.jivesoftware.os.jive.utils.health.api.HealthCheckRegistry;
import com.jivesoftware.os.jive.utils.health.api.HealthChecker;
import com.jivesoftware.os.jive.utils.health.api.HealthFactory;
import com.jivesoftware.os.jive.utils.http.client.HttpClientConfiguration;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactory;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactoryProvider;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.miru.api.MiruBackingStorage;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruLifecyle;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruIBA;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryStore;
import com.jivesoftware.os.miru.cluster.MiruRegistryStoreInitializer;
import com.jivesoftware.os.miru.cluster.MiruReplicaSet;
import com.jivesoftware.os.miru.cluster.MiruReplicaSetDirector;
import com.jivesoftware.os.miru.cluster.rcvs.MiruRCVSClusterRegistry;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.bitmap.SingleBitmapsProvider;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruJustInTimeBackfillerizer;
import com.jivesoftware.os.miru.plugin.schema.SingleSchemaProvider;
import com.jivesoftware.os.miru.service.MiruBackfillerizerInitializer;
import com.jivesoftware.os.miru.service.MiruService;
import com.jivesoftware.os.miru.service.MiruServiceConfig;
import com.jivesoftware.os.miru.service.MiruServiceInitializer;
import com.jivesoftware.os.miru.service.locator.MiruTempDirectoryResourceLocator;
import com.jivesoftware.os.miru.wal.MiruWALInitializer;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALWriter;
import com.jivesoftware.os.miru.wal.activity.MiruWriteToActivityAndSipWAL;
import com.jivesoftware.os.rcvs.api.timestamper.CurrentTimestamper;
import com.jivesoftware.os.rcvs.inmemory.InMemoryRowColumnValueStoreInitializer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.merlin.config.BindInterfaceToConfiguration;
import org.merlin.config.Config;
import org.testng.Assert;

/**
 *
 */
public class MiruPluginTestBootstrap {

    public <BM> MiruProvider<MiruService> bootstrap(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        MiruHost miruHost,
        MiruSchema miruSchema,
        MiruBackingStorage desiredStorage,
        final MiruBitmaps<BM> bitmaps,
        List<MiruPartitionedActivity> partitionedActivities)
        throws Exception {

        HealthFactory.initialize(
            new HealthCheckConfigBinder() {
                @Override
                public <C extends Config> C bindConfig(Class<C> configurationInterfaceClass) {
                    return BindInterfaceToConfiguration.bindDefault(configurationInterfaceClass);
                }
            },
            new HealthCheckRegistry() {
                @Override
                public void register(HealthChecker healthChecker) {
                }

                @Override
                public void unregister(HealthChecker healthChecker) {
                }
            });

        MiruServiceConfig config = BindInterfaceToConfiguration.bindDefault(MiruServiceConfig.class);
        config.setDefaultStorage(desiredStorage.name());
        config.setDefaultFailAfterNMillis(TimeUnit.HOURS.toMillis(1));

        HttpClientFactory httpClientFactory = new HttpClientFactoryProvider()
            .createHttpClientFactory(Collections.<HttpClientConfiguration>emptyList());

        ObjectMapper mapper = new ObjectMapper();

        InMemoryRowColumnValueStoreInitializer inMemoryRowColumnValueStoreInitializer = new InMemoryRowColumnValueStoreInitializer();
        MiruRegistryStore registryStore = new MiruRegistryStoreInitializer().initialize("test", inMemoryRowColumnValueStoreInitializer, mapper);
        MiruClusterRegistry clusterRegistry = new MiruRCVSClusterRegistry(
            new CurrentTimestamper(),
            registryStore.getHostsRegistry(),
            registryStore.getExpectedTenantsRegistry(),
            registryStore.getExpectedTenantPartitionsRegistry(),
            registryStore.getReplicaRegistry(),
            registryStore.getTopologyRegistry(),
            registryStore.getConfigRegistry(),
            registryStore.getWriterPartitionRegistry(),
            3,
            TimeUnit.HOURS.toMillis(1));

        MiruReplicaSetDirector replicaSetDirector = new MiruReplicaSetDirector(new OrderIdProviderImpl(new ConstantWriterIdProvider(1)), clusterRegistry);

        clusterRegistry.sendHeartbeatForHost(miruHost);
        replicaSetDirector.electToReplicaSetForTenantPartition(tenantId, partitionId,
            new MiruReplicaSet(ArrayListMultimap.<MiruPartitionState, MiruPartition>create(), new HashSet<MiruHost>(), 3),
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        clusterRegistry.updateTopology(new MiruPartitionCoord(tenantId, partitionId, miruHost), Optional.<MiruPartitionCoordInfo>absent(),
            Optional.of(System.currentTimeMillis()));

        MiruWALInitializer.MiruWAL wal = new MiruWALInitializer().initialize("test", inMemoryRowColumnValueStoreInitializer, mapper);
        if (!partitionedActivities.isEmpty()) {
            MiruActivityWALWriter activityWALWriter = new MiruWriteToActivityAndSipWAL(wal.getActivityWAL(), wal.getActivitySipWAL());
            activityWALWriter.write(tenantId, partitionedActivities);
        }

        MiruLifecyle<MiruJustInTimeBackfillerizer> backfillerizerLifecycle = new MiruBackfillerizerInitializer().initialize(config, miruHost);

        backfillerizerLifecycle.start();
        final MiruJustInTimeBackfillerizer backfillerizer = backfillerizerLifecycle.getService();

        final MiruActivityInternExtern activityInternExtern = new MiruActivityInternExtern(Interners.<MiruIBA>newWeakInterner(),
            Interners.<MiruTermId>newWeakInterner(), Interners.<MiruTenantId>newWeakInterner(), Interners.<String>newWeakInterner());
        MiruLifecyle<MiruService> miruServiceLifecyle = new MiruServiceInitializer().initialize(config,
            registryStore,
            clusterRegistry,
            miruHost,
            new SingleSchemaProvider(miruSchema),
            wal,
            httpClientFactory,
            new MiruTempDirectoryResourceLocator(),
            activityInternExtern,
            new SingleBitmapsProvider<>(bitmaps));

        miruServiceLifecyle.start();
        final MiruService miruService = miruServiceLifecyle.getService();

        long t = System.currentTimeMillis();
        int maxSecondsToComeOnline = 5 + partitionedActivities.size() / 1_000; // suppose 1K activities/sec
        while (!miruService.checkInfo(tenantId, partitionId, new MiruPartitionCoordInfo(MiruPartitionState.online, desiredStorage))) {
            Thread.sleep(10);
            if (System.currentTimeMillis() - t > TimeUnit.SECONDS.toMillis(maxSecondsToComeOnline)) {
                Assert.fail("Partition failed to come online");
            }
        }

        return new MiruProvider<MiruService>() {
            @Override
            public MiruService getMiru(MiruTenantId tenantId) {
                return miruService;
            }

            @Override
            public MiruActivityInternExtern getActivityInternExtern(MiruTenantId tenantId) {
                return activityInternExtern;
            }

            @Override
            public MiruJustInTimeBackfillerizer getBackfillerizer(MiruTenantId tenantId) {
                return backfillerizer;
            }
        };
    }
}
