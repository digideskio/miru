package com.jivesoftware.os.miru.test;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.miru.api.MiruActorId;
import com.jivesoftware.os.miru.api.MiruTrendingQueryCriteria;
import com.jivesoftware.os.miru.api.MiruTrendingQueryParams;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.api.field.MiruFieldName;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
import com.jivesoftware.os.miru.api.query.filter.MiruFieldFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilterOperation;
import javax.annotation.Nullable;

/**
 *
 */
public class MiruTestTrendyDistributor {

    private final MiruTestFeatureSupplier featureSupplier;
    private final int numTrendy;
    private final int numResultsGlobalTrendy;

    public MiruTestTrendyDistributor(MiruTestFeatureSupplier featureSupplier, int numTrendy, int numResultsGlobalTrendy) {
        this.featureSupplier = featureSupplier;
        this.numTrendy = numTrendy;
        this.numResultsGlobalTrendy = numResultsGlobalTrendy;
    }

    public int getNumTrendy() {
        return numTrendy;
    }

    public MiruTrendingQueryParams globalTrending() {
        Id userId = featureSupplier.oldUsers(1).get(0);
        MiruTrendingQueryCriteria.Builder criteriaBuilder = new MiruTrendingQueryCriteria.Builder()
            .setDesiredNumberOfDistincts(numResultsGlobalTrendy)
            .setConstraintsFilter(new MiruFilter(
                MiruFilterOperation.or,
                Optional.of(ImmutableList.of(viewClassesFilter())),
                Optional.<ImmutableList<MiruFilter>>absent()));

        return new MiruTrendingQueryParams(
            featureSupplier.miruTenantId(),
            Optional.<MiruActorId>absent(),
            Optional.<MiruAuthzExpression>of(new MiruAuthzExpression(Lists.newArrayList(featureSupplier.userAuthz(userId)))),
            criteriaBuilder.build());
    }

    private MiruFieldFilter viewClassesFilter() {
        return new MiruFieldFilter(MiruFieldName.VIEW_CLASS_NAME.getFieldName(), ImmutableList.copyOf(Lists.transform(
            Lists.<String>newArrayList(
                "ContentVersionActivitySearchView",
                "CommentVersionActivitySearchView",
                "LikeActivitySearchView",
                "UserFollowActivitySearchView",
                "MembershipActivitySearchView",
                "PlaceActivitySearchView"),
            CLASS_NAME_TO_TERMID)));
    }

    private static final Function<String, MiruTermId> CLASS_NAME_TO_TERMID = new Function<String, MiruTermId>() {
        @Nullable
        @Override
        public MiruTermId apply(@Nullable String input) {
            return input != null ? new MiruTermId(input.getBytes(Charsets.UTF_8)) : null;
        }
    };
}
