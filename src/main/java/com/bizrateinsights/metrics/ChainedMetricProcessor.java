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
import java.util.List;
import java.util.function.Function;

/**
 * Applies a chain of CloudWatch metric processors.
 *
 * @author Chris McAndrews
 */
public class ChainedMetricProcessor implements Function<MetricDatum, MetricDatum> {

    private final List<Function<MetricDatum, MetricDatum>> delegateProcessors;

    public static ChainedMetricProcessor using(List<Function<MetricDatum, MetricDatum>> delegateProcessors) {
        return new ChainedMetricProcessor(delegateProcessors);
    }

    private ChainedMetricProcessor(List<Function<MetricDatum, MetricDatum>> delegateProcessors) {
        this.delegateProcessors = Collections.unmodifiableList(delegateProcessors);
    }

    @Override
    public MetricDatum apply(MetricDatum metricDatum) {

        MetricDatum result = metricDatum;

        for (Function<MetricDatum, MetricDatum> processor : delegateProcessors) {
            result = processor.apply(result);
        }

        return result;
    }
}
