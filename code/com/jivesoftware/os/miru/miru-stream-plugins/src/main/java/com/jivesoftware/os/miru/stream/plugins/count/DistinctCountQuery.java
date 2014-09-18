package com.jivesoftware.os.miru.stream.plugins.count;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;

/**
 *
 */
public class DistinctCountQuery {

    public final MiruStreamId streamId;
    public final MiruTimeRange timeRange;
    public final MiruFilter streamFilter;
    public final MiruFilter constraintsFilter;
    public final String aggregateCountAroundField;
    public final int desiredNumberOfDistincts;

    public DistinctCountQuery(
            @JsonProperty("streamId") MiruStreamId streamId,
            @JsonProperty("timeRange") MiruTimeRange timeRange,
            @JsonProperty("streamFilter") MiruFilter streamFilter,
            @JsonProperty("constraintsFilter") MiruFilter constraintsFilter,
            @JsonProperty("aggregateCountAroundField") String aggregateCountAroundField,
            @JsonProperty("desiredNumberOfDistincts") int desiredNumberOfDistincts) {
        this.streamId = Preconditions.checkNotNull(streamId);
        this.timeRange = Preconditions.checkNotNull(timeRange);
        this.streamFilter = Preconditions.checkNotNull(streamFilter);
        this.constraintsFilter = Preconditions.checkNotNull(constraintsFilter);
        this.aggregateCountAroundField = Preconditions.checkNotNull(aggregateCountAroundField);
        Preconditions.checkArgument(desiredNumberOfDistincts > 0, "Number of distincts must be at least 1");
        this.desiredNumberOfDistincts = desiredNumberOfDistincts;
    }

    @Override
    public String toString() {
        return "DistinctCountQuery{" +
                "streamId=" + streamId +
                ", timeRange=" + timeRange +
                ", streamFilter=" + streamFilter +
                ", constraintsFilter=" + constraintsFilter +
                ", aggregateCountAroundField='" + aggregateCountAroundField + '\'' +
                ", desiredNumberOfDistincts=" + desiredNumberOfDistincts +
                '}';
    }
}
