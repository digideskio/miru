package com.jivesoftware.os.miru.service.partition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivesoftware.os.jive.utils.http.client.HttpClient;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactory;
import com.jivesoftware.os.jive.utils.http.client.rest.RequestHelper;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.query.partition.MiruHostedPartition;

/** @author jonathan */
public class MiruRemotePartitionFactory {

    private final MiruPartitionInfoProvider partitionInfoProvider;
    private final HttpClientFactory httpClientFactory;
    private final ObjectMapper objectMapper;

    public MiruRemotePartitionFactory(MiruPartitionInfoProvider partitionInfoProvider,
            HttpClientFactory httpClientFactory,
            ObjectMapper objectMapper) {
        this.partitionInfoProvider = partitionInfoProvider;
        this.httpClientFactory = httpClientFactory;
        this.objectMapper = objectMapper;
    }

    public <BM> MiruHostedPartition<BM> create(MiruPartitionCoord coord) throws Exception {
        HttpClient httpClient = httpClientFactory.createClient(coord.host.getLogicalName(), coord.host.getPort());
        RequestHelper requestHelper = new RequestHelper(httpClient, objectMapper);

        return new MiruRemoteHostedPartition<>(coord, partitionInfoProvider, requestHelper);
    }
}
