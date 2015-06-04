package com.jivesoftware.os.miru.service.partition;

import com.jivesoftware.os.jive.utils.http.client.HttpClient;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactory;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.context.MiruRequestContext;
import com.jivesoftware.os.miru.plugin.partition.MiruQueryablePartition;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** @author jonathan */
public class MiruRemoteQueryablePartitionFactory {

    private final HttpClientFactory httpClientFactory;
    private final Map<MiruHost, HttpClient> hostClients = new ConcurrentHashMap<>();

    public MiruRemoteQueryablePartitionFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    private HttpClient hostClient(final MiruPartitionCoord coord) {
        HttpClient client = hostClients.get(coord.host);
        if (client == null) {
            client = httpClientFactory.createClient(coord.host.getLogicalName(), coord.host.getPort());
            hostClients.put(coord.host, client);
        }
        return client;
    }

    public <BM> MiruQueryablePartition<BM> create(final MiruPartitionCoord coord, final MiruPartitionCoordInfo info) {

        final HttpClient httpClient = hostClient(coord);

        return new MiruQueryablePartition<BM>() {

            @Override
            public boolean isLocal() {
                return false;
            }

            @Override
            public MiruPartitionCoord getCoord() {
                return coord;
            }

            @Override
            public MiruRequestHandle<BM> acquireQueryHandle() throws Exception {
                return new MiruRequestHandle<BM>() {

                    @Override
                    public MiruBitmaps<BM> getBitmaps() {
                        return null;
                    }

                    @Override
                    public MiruRequestContext<BM> getRequestContext() {
                        return null;
                    }

                    @Override
                    public boolean isLocal() {
                        return false;
                    }

                    @Override
                    public boolean canBackfill() {
                        return false;
                    }

                    @Override
                    public MiruPartitionCoord getCoord() {
                        return coord;
                    }

                    @Override
                    public HttpClient getHttpClient() {
                        return httpClient;
                    }

                    @Override
                    public void close() throws Exception {
                    }
                };
            }
        };
    }
}
