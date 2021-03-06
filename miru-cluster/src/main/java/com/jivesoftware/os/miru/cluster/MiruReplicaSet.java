package com.jivesoftware.os.miru.cluster;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class MiruReplicaSet {

    private final ListMultimap<MiruPartitionState, MiruPartition> partitionsByState;
    private final Set<MiruHost> hostsWithReplica;
    private final int countOfMissingReplicas;
    private final int desiredNumberOfReplicas;

    public MiruReplicaSet(ListMultimap<MiruPartitionState, MiruPartition> partitionsByState,
        Set<MiruHost> hostsWithReplica,
        int countOfMissingReplicas,
        int desiredNumberOfReplicas) {
        this.partitionsByState = partitionsByState;
        this.hostsWithReplica = hostsWithReplica;
        this.countOfMissingReplicas = countOfMissingReplicas;
        this.desiredNumberOfReplicas = desiredNumberOfReplicas;
    }

    public Collection<MiruPartition> get(MiruPartitionState state) {
        return partitionsByState.get(state);
    }

    public boolean hasPartitionInStates(MiruPartitionState... states) {
        for (MiruPartitionState state : states) {
            if (!partitionsByState.get(state).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<MiruPartition> getAll() {
        return ImmutableList.copyOf(partitionsByState.values());
    }

    public Set<MiruHost> getHostsWithReplica() {
        return hostsWithReplica;
    }

    public int getCountOfMissingReplicas() {
        return countOfMissingReplicas;
    }

    public int getDesiredNumberOfReplicas() {
        return desiredNumberOfReplicas;
    }
}
