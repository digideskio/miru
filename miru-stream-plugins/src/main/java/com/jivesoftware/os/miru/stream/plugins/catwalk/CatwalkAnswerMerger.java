package com.jivesoftware.os.miru.stream.plugins.catwalk;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.api.query.filter.MiruValue;
import com.jivesoftware.os.miru.plugin.solution.MiruAnswerMerger;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;
import com.jivesoftware.os.miru.stream.plugins.catwalk.CatwalkAnswer.FeatureScore;
import com.jivesoftware.os.miru.stream.plugins.fulltext.FullTextAnswer.ActivityScore;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan
 */
public class CatwalkAnswerMerger implements MiruAnswerMerger<CatwalkAnswer> {

    private final int desiredNumberOfResults;

    public CatwalkAnswerMerger(int desiredNumberOfResults) {
        this.desiredNumberOfResults = desiredNumberOfResults;
    }

    /**
     * Merges the last and current results, returning the merged answer.
     *
     * @param last          the last merge result
     * @param currentAnswer the next result to merge
     * @return the merged answer
     */
    @Override
    public CatwalkAnswer merge(Optional<CatwalkAnswer> last, CatwalkAnswer currentAnswer, MiruSolutionLog solutionLog) {
        if (!last.isPresent()) {
            return currentAnswer;
        }

        CatwalkAnswer lastAnswer = last.get();

        if (lastAnswer.results == null) {
            return currentAnswer;
        } else if (currentAnswer.results == null) {
            return lastAnswer;
        }

        Preconditions.checkArgument(currentAnswer.results.length == lastAnswer.results.length, "Misaligned feature arrays");

        @SuppressWarnings("unchecked")
        List<FeatureScore>[] mergedFeatures = new List[currentAnswer.results.length];
        for (int i = 0; i < currentAnswer.results.length; i++) {
            List<FeatureScore> lastFeatures = lastAnswer.results[i];
            List<FeatureScore> currentFeatures = currentAnswer.results[i];

            List<FeatureScore> bigger, smaller;
            if (lastFeatures.size() > currentFeatures.size()) {
                bigger = lastFeatures;
                smaller = currentFeatures;
            } else {
                bigger = currentFeatures;
                smaller = lastFeatures;
            }

            Map<Key, FeatureScore> smallerMap = Maps.newHashMap();
            for (FeatureScore featureScore : smaller) {
                smallerMap.put(new Key(featureScore.values), featureScore);
            }

            List<FeatureScore> merged = Lists.newArrayListWithCapacity(bigger.size() + smaller.size());
            for (FeatureScore featureScore : bigger) {
                FeatureScore otherScore = smallerMap.remove(new Key(featureScore.values));
                if (otherScore != null) {
                    merged.add(new FeatureScore(featureScore.values,
                        featureScore.numerator + otherScore.numerator,
                        featureScore.denominator + otherScore.denominator));
                } else {
                    merged.add(featureScore);
                }
            }
            merged.addAll(smallerMap.values());
            mergedFeatures[i] = merged;
        }

        return new CatwalkAnswer(mergedFeatures, currentAnswer.resultsExhausted);
    }

    @Override
    public CatwalkAnswer done(Optional<CatwalkAnswer> last, CatwalkAnswer alternative, MiruSolutionLog solutionLog) {
        return last.or(alternative);
    }

    private static class Key {
        private final MiruValue[] values;

        public Key(MiruValue[] values) {
            this.values = values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!Arrays.equals(values, key.values)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return values != null ? Arrays.hashCode(values) : 0;
        }
    }
}
