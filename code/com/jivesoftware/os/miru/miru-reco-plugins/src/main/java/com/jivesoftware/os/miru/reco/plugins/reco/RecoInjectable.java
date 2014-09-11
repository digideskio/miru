package com.jivesoftware.os.miru.reco.plugins.reco;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.query.Miru;
import com.jivesoftware.os.miru.query.MiruProvider;
import com.jivesoftware.os.miru.query.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.query.solution.MiruResponse;
import com.jivesoftware.os.miru.query.solution.MiruSolvableFactory;

/**
 *
 */
public class RecoInjectable {

    private final MiruProvider miruProvider;
    private final CollaborativeFiltering collaborativeFiltering;

    public RecoInjectable(MiruProvider<? extends Miru> miruProvider, CollaborativeFiltering collaborativeFiltering) {
        this.miruProvider = miruProvider;
        this.collaborativeFiltering = collaborativeFiltering;
    }

    public MiruResponse<RecoAnswer> collaborativeFilteringRecommendations(RecoQuery query) throws MiruQueryServiceException {
        try {
            MiruTenantId tenantId = query.tenantId;
            Miru miru = miruProvider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                    new MiruSolvableFactory<>("collaborativeFilteringRecommendations", new RecoQuestion(collaborativeFiltering, query)),
                    new RecoAnswerEvaluator(query),
                    new RecoAnswerMerger(query.desiredNumberOfDistincts),
                    RecoAnswer.EMPTY_RESULTS);
        } catch (MiruPartitionUnavailableException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to score reco stream", e);
        }
    }

    public RecoAnswer collaborativeFilteringRecommendations(MiruPartitionId partitionId, RecoQueryAndReport queryAndReport)
            throws MiruQueryServiceException {
        try {
            MiruTenantId tenantId = queryAndReport.query.tenantId;
            Miru miru = miruProvider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                    partitionId,
                    new MiruSolvableFactory<>("collaborativeFilteringRecommendations", new RecoQuestion(collaborativeFiltering, queryAndReport.query)),
                    Optional.fromNullable(queryAndReport.report),
                    RecoAnswer.EMPTY_RESULTS);
        } catch (MiruPartitionUnavailableException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to score reco stream for partition: " + partitionId.getId(), e);
        }
    }

}
