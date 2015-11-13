package com.jivesoftware.os.miru.plugin;

import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.backfill.MiruJustInTimeBackfillerizer;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruTermComposer;
import com.jivesoftware.os.miru.plugin.query.MiruQueryParser;
import com.jivesoftware.os.miru.plugin.solution.MiruRemotePartition;
import com.jivesoftware.os.routing.bird.http.client.ConnectionDescriptorSelectiveStrategy;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import java.util.Map;

/**
 *
 */
public interface MiruProvider<T extends Miru> {

    MiruStats getStats();

    T getMiru(MiruTenantId tenantId);

    MiruActivityInternExtern getActivityInternExtern(MiruTenantId tenantId);

    MiruJustInTimeBackfillerizer getBackfillerizer(MiruTenantId tenantId);

    MiruTermComposer getTermComposer();

    MiruQueryParser getQueryParser(String defaultField);

    <R extends MiruRemotePartition<?, ?, ?>> R getRemotePartition(Class<R> remotePartitionClass);

    TenantAwareHttpClient<String> getReaderHttpClient();

    Map<MiruHost, ConnectionDescriptorSelectiveStrategy> getReaderStrategyCache();
}
