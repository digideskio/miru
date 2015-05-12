package com.jivesoftware.os.miru.stumptown.plugins;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.Miru;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestAndReport;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionMarshaller;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvableFactory;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;

/**
 *
 */
public class StumptownInjectable {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruProvider<? extends Miru> provider;
    private final Stumptown stumptown;
    private final MiruSolutionMarshaller<StumptownQuery, StumptownAnswer, StumptownReport> marshaller;

    public StumptownInjectable(MiruProvider<? extends Miru> provider, Stumptown stumptown,
        MiruSolutionMarshaller<StumptownQuery, StumptownAnswer, StumptownReport> marshaller) {
        this.provider = provider;
        this.stumptown = stumptown;
        this.marshaller = marshaller;
    }

    public MiruResponse<StumptownAnswer> score(MiruRequest<StumptownQuery> request) throws MiruQueryServiceException {
        try {
            LOG.debug("askAndMerge: request={}", request);
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                new MiruSolvableFactory<>(provider.getStats(), "scoreStumptown", new StumptownQuestion(stumptown, request), marshaller),
                new StumptownAnswerEvaluator(),
                new StumptownAnswerMerger(request.query.desiredNumberOfResultsPerWaveform),
                StumptownAnswer.EMPTY_RESULTS,
                request.logLevel);
        } catch (MiruPartitionUnavailableException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to score trending stream", e);
        }
    }

    public MiruPartitionResponse<StumptownAnswer> score(MiruPartitionId partitionId,
        MiruRequestAndReport<StumptownQuery, StumptownReport> requestAndReport) throws MiruQueryServiceException {
        try {
            LOG.debug("askImmediate: partitionId={} request={}", partitionId, requestAndReport.request);
            LOG.trace("askImmediate: report={}", requestAndReport.report);
            MiruTenantId tenantId = requestAndReport.request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                partitionId,
                new MiruSolvableFactory<>(provider.getStats(), "scoreTrending", new StumptownQuestion(stumptown, requestAndReport.request), marshaller),
                Optional.fromNullable(requestAndReport.report),
                StumptownAnswer.EMPTY_RESULTS,
                requestAndReport.request.logLevel);
        } catch (MiruPartitionUnavailableException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to score trending stream for partition: " + partitionId.getId(), e);
        }
    }

}
