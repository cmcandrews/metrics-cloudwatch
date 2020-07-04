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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Applies a chain of duplicating CloudWatch metric processors, including the original metric datum in the resulting
 * set.
 *
 * @author Chris McAndrews
 */
public class ChainedDuplicatingMetricProcessor implements Function<MetricDatum, Stream<MetricDatum>> {

    private final List<Function<MetricDatum, Set<MetricDatum>>> delegateProcessors;

    public static ChainedDuplicatingMetricProcessor using(List<Function<MetricDatum, Set<MetricDatum>>> processors) {
        return new ChainedDuplicatingMetricProcessor(processors);
    }

    public ChainedDuplicatingMetricProcessor(List<Function<MetricDatum, Set<MetricDatum>>> delegateProcessors) {
        this.delegateProcessors = Collections.unmodifiableList(delegateProcessors);
    }

    @Override
    public Stream<MetricDatum> apply(MetricDatum metricDatum) {

        final Set<MetricDatum> result = new HashSet<>();
        result.add(metricDatum);

        for (Function<MetricDatum, Set<MetricDatum>> delegate : delegateProcessors) {
            result.addAll(Optional.ofNullable(delegate.apply(metricDatum)).orElse(Collections.emptySet()));
        }

        return result.stream();
    }
}
