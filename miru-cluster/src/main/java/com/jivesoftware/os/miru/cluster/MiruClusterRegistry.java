package com.jivesoftware.os.miru.cluster;

import com.google.common.base.Optional;
import com.jivesoftware.os.jive.utils.base.interfaces.CallbackStream;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.api.MiruTopologyStatus;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.topology.HostHeartbeat;
import com.jivesoftware.os.miru.api.topology.MiruIngressUpdate;
import com.jivesoftware.os.miru.api.topology.MiruPartitionActiveUpdate;
import com.jivesoftware.os.miru.api.topology.MiruTenantConfig;
import com.jivesoftware.os.miru.api.topology.MiruTenantTopologyUpdate;
import com.jivesoftware.os.miru.api.topology.NamedCursor;
import com.jivesoftware.os.miru.api.topology.NamedCursorsResult;
import com.jivesoftware.os.miru.api.topology.RangeMinMax;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public interface MiruClusterRegistry {

    void heartbeat(MiruHost miruHost) throws Exception;

    LinkedHashSet<HostHeartbeat> getAllHosts() throws Exception;

    MiruTenantConfig getTenantConfig(MiruTenantId tenantId) throws Exception;

    int getNumberOfReplicas(MiruTenantId tenantId) throws Exception;

    List<MiruTenantId> getTenantsForHost(MiruHost miruHost) throws Exception;

    void addToReplicaRegistry(MiruTenantId tenantId, MiruPartitionId partitionId, long nextId, MiruHost host) throws Exception;

    void removeTenantPartionReplicaSet(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception;

    void ensurePartitionCoord(MiruPartitionCoord coord) throws Exception;

    List<MiruPartition> getPartitionsForTenant(MiruTenantId tenantId) throws Exception;

    List<MiruPartition> getPartitionsForTenantHost(MiruTenantId tenantId, MiruHost host) throws Exception;

    List<MiruTopologyStatus> getTopologyStatusForTenant(MiruTenantId tenantId) throws Exception;

    List<MiruTopologyStatus> getTopologyStatusForTenantHost(MiruTenantId tenantId, MiruHost host) throws Exception;

    MiruReplicaSet getReplicaSet(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception;

    Map<MiruPartitionId, MiruReplicaSet> getReplicaSets(MiruTenantId tenantId, Collection<MiruPartitionId> requiredPartitionId) throws Exception;

    void updateIngress(Collection<MiruIngressUpdate> ingressUpdates) throws Exception;

    void updateTopologies(MiruHost host, Collection<TopologyUpdate> topologyUpdates) throws Exception;

    NamedCursorsResult<Collection<MiruTenantTopologyUpdate>> getTopologyUpdatesForHost(MiruHost host,
        Collection<NamedCursor> sinceCursors) throws Exception;

    NamedCursorsResult<Collection<MiruPartitionActiveUpdate>> getPartitionActiveUpdatesForHost(MiruHost host,
        Collection<NamedCursor> sinceCursors) throws Exception;

    void removeHost(MiruHost host) throws Exception;

    void removeTopology(MiruTenantId tenantId, MiruPartitionId partitionId, MiruHost host) throws Exception;

    void topologiesForTenants(List<MiruTenantId> tenantIds, final CallbackStream<MiruTopologyStatus> callbackStream) throws Exception;

    MiruSchema getSchema(MiruTenantId tenantId) throws Exception;

    void registerSchema(MiruTenantId tenantId, MiruSchema schema) throws Exception;

    Map<MiruPartitionId, RangeMinMax> getIngressRanges(MiruTenantId tenantId) throws Exception;

    class TopologyUpdate {
        public final MiruPartitionCoord coord;
        public final Optional<MiruPartitionCoordInfo> optionalInfo;
        public final Optional<Long> queryTimestamp;

        public TopologyUpdate(MiruPartitionCoord coord,
            Optional<MiruPartitionCoordInfo> optionalInfo,
            Optional<Long> queryTimestamp) {
            this.coord = coord;
            this.optionalInfo = optionalInfo;
            this.queryTimestamp = queryTimestamp;
        }
    }
}
