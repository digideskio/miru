package com.jivesoftware.os.miru.stream.plugins.count;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.activity.schema.MiruFieldDefinition;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.api.field.MiruFieldType;
import com.jivesoftware.os.miru.plugin.bitmap.CardinalityAndLastSetBit;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.context.MiruRequestContext;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruTermComposer;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class DistinctCount {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    public <BM extends IBM, IBM> DistinctCountAnswer numberOfDistincts(MiruBitmaps<BM, IBM> bitmaps,
        MiruRequestContext<IBM, ?> requestContext,
        MiruRequest<DistinctCountQuery> request,
        Optional<DistinctCountReport> lastReport,
        BM answer)
        throws Exception {

        StackBuffer stackBuffer = new StackBuffer();

        log.debug("Get number of distincts for answer={} query={}", answer, request);

        int collectedDistincts = 0;
        Set<String> aggregateTerms;
        if (lastReport.isPresent()) {
            collectedDistincts = lastReport.get().collectedDistincts;
            aggregateTerms = Sets.newHashSet(lastReport.get().aggregateTerms);
        } else {
            aggregateTerms = Sets.newHashSet();
        }

        MiruTermComposer termComposer = requestContext.getTermComposer();
        int fieldId = requestContext.getSchema().getFieldId(request.query.aggregateCountAroundField);
        MiruFieldDefinition fieldDefinition = requestContext.getSchema().getFieldDefinition(fieldId);
        log.debug("fieldId={}", fieldId);
        if (fieldId >= 0) {
            MiruFieldIndex<IBM> fieldIndex = requestContext.getFieldIndexProvider().getFieldIndex(MiruFieldType.primary);

            for (String aggregateTerm : aggregateTerms) {
                MiruTermId aggregateTermId = termComposer.compose(fieldDefinition, aggregateTerm);
                Optional<IBM> optionalTermIndex = fieldIndex.get(fieldId, aggregateTermId).getIndex(stackBuffer);
                if (!optionalTermIndex.isPresent()) {
                    continue;
                }

                IBM termIndex = optionalTermIndex.get();
                answer = bitmaps.andNot(answer, termIndex);
            }

            CardinalityAndLastSetBit<BM> answerCollector = null;
            while (true) {
                int lastSetBit = answerCollector == null ? bitmaps.lastSetBit(answer) : answerCollector.lastSetBit;
                log.trace("lastSetBit={}", lastSetBit);
                if (lastSetBit < 0) {
                    break;
                }
                MiruTermId[] fieldValues = requestContext.getActivityIndex().get(lastSetBit, fieldId, stackBuffer);
                log.trace("fieldValues={}", (Object) fieldValues);
                if (fieldValues == null || fieldValues.length == 0) {
                    // could make this a reusable buffer, but this is effectively an error case and would require 3 buffers
                    BM removeUnknownField = bitmaps.createWithBits(lastSetBit);
                    answerCollector = bitmaps.andNotWithCardinalityAndLastSetBit(answer, removeUnknownField);
                    answer = answerCollector.bitmap;

                } else {
                    MiruTermId aggregateTermId = fieldValues[0];
                    String aggregateTerm = termComposer.decompose(fieldDefinition, aggregateTermId);

                    aggregateTerms.add(aggregateTerm);
                    Optional<IBM> optionalTermIndex = fieldIndex.get(fieldId, aggregateTermId).getIndex(stackBuffer);
                    checkState(optionalTermIndex.isPresent(), "Unable to load inverted index for aggregateTermId: " + aggregateTermId);

                    answerCollector = bitmaps.andNotWithCardinalityAndLastSetBit(answer, optionalTermIndex.get());
                    answer = answerCollector.bitmap;

                    collectedDistincts++;

                    if (collectedDistincts > request.query.desiredNumberOfDistincts) {
                        break;
                    }
                }
            }
        }

        boolean resultsExhausted = request.query.timeRange.smallestTimestamp > requestContext.getTimeIndex().getLargestTimestamp();
        DistinctCountAnswer result = new DistinctCountAnswer(aggregateTerms, collectedDistincts, resultsExhausted);
        log.debug("result={}", result);
        return result;
    }

}
