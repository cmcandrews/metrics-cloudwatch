/*
   Copyright 2017 Bizrate Insights, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.bizrateinsights.metrics;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

/**
 * Applies a chain of duplicating CloudWatch metric processors, including the original metric datum in the resulting
 * set.
 *
 * @author Chris McAndrews
 */
public class ChainedDuplicatingMetricProcessor implements Function<MetricDatum, Set<MetricDatum>> {

    private final List<Function<MetricDatum, Set<MetricDatum>>> delegateProcessors;

    public static ChainedDuplicatingMetricProcessor using(List<Function<MetricDatum, Set<MetricDatum>>> processors) {
        return new ChainedDuplicatingMetricProcessor(processors);
    }

    public ChainedDuplicatingMetricProcessor(List<Function<MetricDatum, Set<MetricDatum>>> delegateProcessors) {
        this.delegateProcessors = ImmutableList.copyOf(delegateProcessors);
    }

    @Override
    public Set<MetricDatum> apply(MetricDatum metricDatum) {

        final Set<MetricDatum> result = Sets.newHashSet(metricDatum);

        for (Function<MetricDatum, Set<MetricDatum>> delegate : delegateProcessors) {
            result.addAll(Optional.fromNullable(delegate.apply(metricDatum)).or(ImmutableSet.<MetricDatum>of()));
        }

        return ImmutableSet.copyOf(result);
    }
}
