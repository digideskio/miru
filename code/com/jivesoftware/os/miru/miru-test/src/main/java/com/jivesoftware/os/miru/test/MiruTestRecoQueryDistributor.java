package com.jivesoftware.os.miru.test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.jive.utils.ordered.id.TimestampProvider;
import com.jivesoftware.os.miru.api.field.MiruFieldName;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
import com.jivesoftware.os.miru.api.query.filter.MiruFieldFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilterOperation;
import com.jivesoftware.os.miru.query.solution.MiruTimeRange;
import com.jivesoftware.os.miru.reco.plugins.reco.RecoQuery;
import com.jivesoftware.os.miru.reco.plugins.trending.TrendingQuery;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class MiruTestRecoQueryDistributor {

    private final int numQueries;
    private final MiruTestFeatureSupplier featureSupplier;
    private final int numResultsGlobalTrendy;
    private final int numResultsCollaborativeFiltering;
    private final SnowflakeIdPacker snowflakeIdPacker = new SnowflakeIdPacker();
    private final TimestampProvider timestampProvider = new JiveEpochTimestampProvider();

    public MiruTestRecoQueryDistributor(int numQueries, MiruTestFeatureSupplier featureSupplier, int numResultsGlobalTrendy, int
            numResultsCollaborativeFiltering) {
        this.numQueries = numQueries;
        this.featureSupplier = featureSupplier;
        this.numResultsGlobalTrendy = numResultsGlobalTrendy;
        this.numResultsCollaborativeFiltering = numResultsCollaborativeFiltering;
    }

    public int getNumQueries() {
        return numQueries;
    }

    public TrendingQuery globalTrending() {
        Id userId = featureSupplier.oldUsers(1).get(0);
        long packCurrentTime = snowflakeIdPacker.pack(timestampProvider.getTimestamp(), 0, 0);
        long packThreeDays = snowflakeIdPacker.pack(TimeUnit.DAYS.toMillis(3), 0, 0);
        return new TrendingQuery(
                featureSupplier.miruTenantId(),
                new MiruTimeRange(packCurrentTime - packThreeDays, packCurrentTime),
                32,
                new MiruFilter(
                        MiruFilterOperation.or,
                        Optional.of(ImmutableList.of(viewClassesFilter())),
                        Optional.<ImmutableList<MiruFilter>>absent()),
                new MiruAuthzExpression(Lists.newArrayList(featureSupplier.userAuthz(userId))),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                numResultsGlobalTrendy);
    }

    public RecoQuery collaborativeFiltering() {
        Id userId = featureSupplier.oldUsers(1).get(0);
        MiruFieldFilter miruFieldFilter = new MiruFieldFilter(
                MiruFieldName.AUTHOR_ID.getFieldName(), Arrays.asList(userId.toStringForm()));
        MiruFilter filter = new MiruFilter(MiruFilterOperation.or, Optional.of(ImmutableList.of(miruFieldFilter)),
                Optional.<ImmutableList<MiruFilter>>absent());
        return new RecoQuery(
                featureSupplier.miruTenantId(),
                filter,
                new MiruAuthzExpression(Lists.newArrayList(featureSupplier.userAuthz(userId))),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                MiruFieldName.AUTHOR_ID.getFieldName(),
                MiruFieldName.AUTHOR_ID.getFieldName(),
                MiruFieldName.AUTHOR_ID.getFieldName(),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                MiruFieldName.ACTIVITY_PARENT.getFieldName(),
                numResultsCollaborativeFiltering);
    }

    private MiruFieldFilter viewClassesFilter() {
        return new MiruFieldFilter(MiruFieldName.VIEW_CLASS_NAME.getFieldName(), ImmutableList.of(
                        "ContentVersionActivitySearchView",
                        "CommentVersionActivitySearchView",
                        "LikeActivitySearchView",
                        "UserFollowActivitySearchView",
                        "MembershipActivitySearchView",
                        "PlaceActivitySearchView"));
    }

}
