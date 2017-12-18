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
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Chris McAndrews
 */
public final class MetricsCollector {
    private final String prefix;
    private final Map<String, String> tags;
    private final long timestamp;
    private final Set<MetricDatum> metrics = new HashSet<MetricDatum>();

    private MetricsCollector(String prefix, Map<String, String> tags, long timestamp) {
        this.prefix = prefix;
        this.tags = tags;
        this.timestamp = timestamp;
    }

    public static MetricsCollector createNew(String prefix, Map<String, String> tags, long timestamp) {
        return new MetricsCollector(prefix, tags, timestamp);
    }

    public MetricsCollector addMetric(String metricName, Object value, String unit) {
        final MetricDatum metricDatum = new MetricDatum()
                .withMetricName(MetricRegistry.name(prefix, metricName))
                .withTimestamp(new Date(timestamp))
                .withValue(Double.valueOf(value.toString()))
                .withUnit(cloudWatchUnit(unit).or(StandardUnit.None))
                .withDimensions(FluentIterable.from(tags.entrySet()).transform(TagToDimension.INSTANCE).toSet());

        this.metrics.add(metricDatum);
        return this;
    }

    public MetricsCollector addMetric(String metricName, Object value) {
        final MetricDatum metricDatum = new MetricDatum()
                .withMetricName(MetricRegistry.name(prefix, metricName))
                .withTimestamp(new Date(timestamp))
                .withValue(Double.valueOf(value.toString()))
                .withDimensions(FluentIterable.from(tags.entrySet()).transform(TagToDimension.INSTANCE).toSet());

        this.metrics.add(metricDatum);
        return this;
    }

    public Set<MetricDatum> build() {
        return metrics;
    }

    // CloudWatch enum capitalizes its values while metrics uses lowercase, so StandardUnit.valueOf() is not
    // sufficient
    private Optional<StandardUnit> cloudWatchUnit(String unit) {

        if ("calls/second".equals(unit)) {
            return Optional.of(StandardUnit.CountSecond);
        }

        for (StandardUnit standardUnit : StandardUnit.values()) {
            if (standardUnit.name().toLowerCase().equals(unit.toLowerCase())) {
                return Optional.of(standardUnit);
            }
        }

        return Optional.absent();
    }
}