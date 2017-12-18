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

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

/**
 * @author Chris McAndrews
 */
public class MetricsCollectorTest {

    @Test
    public void shouldCollectMetricsWithNoUnits() {
        // Given
        final String prefix = "";
        final long timestamp = System.currentTimeMillis();
        final Map<String, String> tags = ImmutableMap.of("key", "value");

        final String metric1Name = "metric1";
        final String metric1Value = "1";

        final String metric2Name = "metric2";
        final int metric2Value = 2;

        // When
        final Set<MetricDatum> result = MetricsCollector.createNew(prefix, tags, timestamp)
                .addMetric(metric1Name, metric1Value)
                .addMetric(metric2Name, metric2Value)
                .build();

        // Then
        final Set<Dimension> expectedDimensions = ImmutableSet.of(new Dimension().withName("key").withValue("value"));
        final MetricDatum expectedMetric1 = new MetricDatum()
                .withMetricName(metric1Name)
                .withTimestamp(new Date(timestamp))
                .withValue(1.0)
                .withDimensions(expectedDimensions);
        final MetricDatum expectedMetric2 = new MetricDatum()
                .withMetricName(metric2Name)
                .withTimestamp(new Date(timestamp))
                .withValue(2.0)
                .withDimensions(expectedDimensions);

        assertThat(result, containsInAnyOrder(expectedMetric1, expectedMetric2));
    }

    @Test
    public void shouldCollectMetricsWithUnits() {
        // Given
        final String prefix = "";
        final long timestamp = System.currentTimeMillis();
        final Map<String, String> tags = ImmutableMap.of("key", "value");

        final String metric1Name = "metric1";
        final String metric1Value = "1";
        final String metric1Unit = "seconds";

        final String metric2Name = "metric2";
        final int metric2Value = 2;
        final String metric2Unit = "bytes";

        // When
        final Set<MetricDatum> result = MetricsCollector.createNew(prefix, tags, timestamp)
                .addMetric(metric1Name, metric1Value, metric1Unit)
                .addMetric(metric2Name, metric2Value, metric2Unit)
                .build();

        // Then
        final Set<Dimension> expectedDimensions = ImmutableSet.of(new Dimension().withName("key").withValue("value"));
        final MetricDatum expectedMetric1 = new MetricDatum()
                .withMetricName(metric1Name)
                .withTimestamp(new Date(timestamp))
                .withValue(1.0)
                .withDimensions(expectedDimensions)
                .withUnit(StandardUnit.Seconds);
        final MetricDatum expectedMetric2 = new MetricDatum()
                .withMetricName(metric2Name)
                .withTimestamp(new Date(timestamp))
                .withValue(2.0)
                .withDimensions(expectedDimensions)
                .withUnit(StandardUnit.Bytes);

        assertThat(result, containsInAnyOrder(expectedMetric1, expectedMetric2));
    }

    @Test
    public void shouldCollectMetricsWithUnknownUnits() {
        // Given
        final String prefix = "";
        final long timestamp = System.currentTimeMillis();
        final Map<String, String> tags = ImmutableMap.of("key", "value");

        final String metricName = "metric1";
        final String metricValue = "1";
        final String metricUnit = "?";

        // When
        final Set<MetricDatum> result = MetricsCollector.createNew(prefix, tags, timestamp)
                .addMetric(metricName, metricValue, metricUnit)
                .build();

        // Then
        final Set<Dimension> expectedDimensions = ImmutableSet.of(new Dimension().withName("key").withValue("value"));
        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName(metricName)
                .withTimestamp(new Date(timestamp))
                .withValue(1.0)
                .withDimensions(expectedDimensions)
                .withUnit(StandardUnit.None);

        assertThat(result, contains(expectedMetric));
    }

    @Test
    public void shouldCollectMetricsWithRateUnits() {
        final String prefix = "";
        final long timestamp = System.currentTimeMillis();
        final Map<String, String> tags = ImmutableMap.of("key", "value");

        final String metricName = "metric1";
        final String metricValue = "1";
        final String metricUnit = "calls/second";

        // When
        final Set<MetricDatum> result = MetricsCollector.createNew(prefix, tags, timestamp)
                .addMetric(metricName, metricValue, metricUnit)
                .build();

        // Then
        final Set<Dimension> expectedDimensions = ImmutableSet.of(new Dimension().withName("key").withValue("value"));
        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName(metricName)
                .withTimestamp(new Date(timestamp))
                .withValue(1.0)
                .withDimensions(expectedDimensions)
                .withUnit(StandardUnit.CountSecond);

        assertThat(result, contains(expectedMetric));
    }
}