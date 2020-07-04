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

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * @author Chris McAndrews
 */
@RunWith(MockitoJUnitRunner.class)
public class CloudWatchMetricsReporterTest {

    @Mock
    private MetricRegistry metricRegistry;

    @Mock
    private AmazonCloudWatchAsyncClient cloudWatch;

    @Mock
    private Gauge gauge;

    @Mock
    private Counter counter;

    @Mock
    private Histogram histogram;

    @Mock
    private Meter meter;

    @Mock
    private Timer timer;

    @Mock
    private Clock clock;

    @Mock
    private Snapshot snapshot;

    private static final String METRIC_NAMESPACE = "application";

    @Test
    public void shouldNotReportIfDisabled() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .setEnabled(false)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(gauge.getValue()).willReturn(1);

        final SortedMap<String, Gauge> gauges = new TreeMap<>();
        gauges.put("gauge", gauge);
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        then(cloudWatch).should(times(0)).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void shouldReportGaugesToCloudWatchWithTags() {
        // Given
        final Map<String, String> tagMap = new HashMap<>();
        tagMap.put("key", "value");
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .withTags(tagMap)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(gauge.getValue()).willReturn(1);

        final SortedMap<String, Gauge> gauges = new TreeMap<>();
        gauges.put("gauge", gauge);
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<Dimension> dimensionList = new ArrayList<>();
        dimensionList.add(new Dimension().withName("key").withValue("value"));

        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName("gauge.value")
                .withValue(1.0)
                .withDimensions(dimensionList)
                .withTimestamp(new Date(timestamp));
        assertThat(request.getMetricData(), contains(expectedMetric));
    }

    @Test
    public void shouldReportGaugesToCloudWatch() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(gauge.getValue()).willReturn(1);

        final SortedMap<String, Gauge> gauges = new TreeMap<>();
        gauges.put("gauge", gauge);
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName("gauge.value")
                .withValue(1.0)
                .withTimestamp(new Date(timestamp));
        assertThat(request.getMetricData(), contains(expectedMetric));
    }

    @Test
    public void shouldFilterNonNumericGauges() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        final Gauge mockGauge = mock(Gauge.class);

        given(gauge.getValue()).willReturn(1);
        given(mockGauge.getValue()).willReturn("aaaa");

        final SortedMap<String, Gauge> gauges = new TreeMap<>();
        gauges.put("gauge", gauge);
        gauges.put("mockGauge", mockGauge);
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName("gauge.value")
                .withValue(1.0)
                .withTimestamp(new Date(timestamp));
        assertThat(request.getMetricData(), contains(expectedMetric));
    }

    @Test
    public void shouldReportCountersToCloudWatch() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(counter.getCount()).willReturn(1L);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = new TreeMap<>();
        counters.put("counter", counter);
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final MetricDatum expectedMetric = new MetricDatum()
                .withMetricName("counter.count")
                .withValue(1.0)
                .withTimestamp(new Date(timestamp));
        assertThat(request.getMetricData(), contains(expectedMetric));
    }

    @Test
    public void shouldReportHistogramsToCloudWatch() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(histogram.getCount()).willReturn(1L);
        given(histogram.getSnapshot()).willReturn(snapshot);
        given(snapshot.getMax()).willReturn(2L);
        given(snapshot.getMin()).willReturn(3L);
        given(snapshot.getMean()).willReturn(4.0);
        given(snapshot.getStdDev()).willReturn(5.0);
        given(snapshot.getMedian()).willReturn(6.0);
        given(snapshot.get75thPercentile()).willReturn(7.0);
        given(snapshot.get95thPercentile()).willReturn(8.0);
        given(snapshot.get98thPercentile()).willReturn(9.0);
        given(snapshot.get99thPercentile()).willReturn(10.0);
        given(snapshot.get999thPercentile()).willReturn(11.0);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = new TreeMap<>();
        histograms.put("histogram", histogram);
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics = new ArrayList<>();
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.count").withValue(1.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.max").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.min").withValue(3.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.mean").withValue(4.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.stddev").withValue(5.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.median").withValue(6.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.p75").withValue(7.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.p95").withValue(8.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.p98").withValue(9.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.p99").withValue(10.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("histogram.p999").withValue(11.0).withTimestamp(new Date(timestamp)));

        assertThat(request.getMetricData(), containsInAnyOrder(expectedMetrics.toArray()));
    }

    @Test
    public void shouldReportMetersToCloudWatch() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(meter.getCount()).willReturn(1L);
        given(meter.getMeanRate()).willReturn(2.0);
        given(meter.getOneMinuteRate()).willReturn(3.0);
        given(meter.getFiveMinuteRate()).willReturn(4.0);
        given(meter.getFifteenMinuteRate()).willReturn(5.0);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = new TreeMap<>();
        meters.put("meter", meter);
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics = new ArrayList<>();
        expectedMetrics.add(new MetricDatum().withMetricName("meter.count").withValue(1.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("meter.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("meter.m1").withValue(3.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("meter.m5").withValue(4.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("meter.m15").withValue(5.0).withTimestamp(new Date(timestamp)));

        assertThat(request.getMetricData(), containsInAnyOrder(expectedMetrics.toArray()));
    }

    @Test
    public void shouldReportMetersToCloudWatchWithCounterDifferential() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(meter.getCount()).willReturn(1L).willReturn(3L);
        given(meter.getMeanRate()).willReturn(2.0);
        given(meter.getOneMinuteRate()).willReturn(3.0);
        given(meter.getFiveMinuteRate()).willReturn(4.0);
        given(meter.getFifteenMinuteRate()).willReturn(5.0);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = new TreeMap<>();
        meters.put("meter", meter);
        final SortedMap<String, Timer> timers = Collections.emptySortedMap();

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should(times(2)).putMetricDataAsync(requestCaptor.capture());

        final List<PutMetricDataRequest> requests = requestCaptor.getAllValues();

        assertThat(requests.get(0).getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics1 = new ArrayList<>();
        expectedMetrics1.add(new MetricDatum().withMetricName("meter.count").withValue(1.0).withTimestamp(new Date(timestamp)));
        expectedMetrics1.add(new MetricDatum().withMetricName("meter.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics1.add(new MetricDatum().withMetricName("meter.m1").withValue(3.0).withTimestamp(new Date(timestamp)));
        expectedMetrics1.add(new MetricDatum().withMetricName("meter.m5").withValue(4.0).withTimestamp(new Date(timestamp)));
        expectedMetrics1.add(new MetricDatum().withMetricName("meter.m15").withValue(5.0).withTimestamp(new Date(timestamp)));

        assertThat(requests.get(0).getMetricData(), containsInAnyOrder(expectedMetrics1.toArray()));

        assertThat(requests.get(1).getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics2 = new ArrayList<>();
        expectedMetrics2.add(new MetricDatum().withMetricName("meter.count").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics2.add(new MetricDatum().withMetricName("meter.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics2.add(new MetricDatum().withMetricName("meter.m1").withValue(3.0).withTimestamp(new Date(timestamp)));
        expectedMetrics2.add(new MetricDatum().withMetricName("meter.m5").withValue(4.0).withTimestamp(new Date(timestamp)));
        expectedMetrics2.add(new MetricDatum().withMetricName("meter.m15").withValue(5.0).withTimestamp(new Date(timestamp)));

        assertThat(requests.get(1).getMetricData(), containsInAnyOrder(expectedMetrics2.toArray()));
    }

    @Test
    public void shouldReportTimersToCloudWatch() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(timer.getCount()).willReturn(1L);
        given(timer.getMeanRate()).willReturn(2.0);
        given(timer.getOneMinuteRate()).willReturn(3.0);
        given(timer.getFiveMinuteRate()).willReturn(4.0);
        given(timer.getFifteenMinuteRate()).willReturn(5.0);
        given(timer.getSnapshot()).willReturn(snapshot);
        given(snapshot.getMax()).willReturn(6L);
        given(snapshot.getMin()).willReturn(7L);
        given(snapshot.getMean()).willReturn(8.0);
        given(snapshot.getStdDev()).willReturn(9.0);
        given(snapshot.getMedian()).willReturn(1.0);
        given(snapshot.get75thPercentile()).willReturn(2.0);
        given(snapshot.get95thPercentile()).willReturn(3.0);
        given(snapshot.get98thPercentile()).willReturn(4.0);
        given(snapshot.get99thPercentile()).willReturn(5.0);
        given(snapshot.get999thPercentile()).willReturn(6.0);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = new TreeMap<>();
        timers.put("timer", timer);

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should().putMetricDataAsync(requestCaptor.capture());

        final PutMetricDataRequest request = requestCaptor.getValue();
        assertThat(request.getNamespace(), is(equalTo(METRIC_NAMESPACE)));


        final List<MetricDatum> expectedMetrics = new ArrayList<>();
        expectedMetrics.add(new MetricDatum().withMetricName("timer.count").withValue(1.0).withTimestamp(new Date(timestamp)));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.m1").withValue(3.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.m5").withValue(4.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.m15").withValue(5.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.max").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.min").withValue(0.007).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.mean").withValue(0.008).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.stddev").withValue(0.009).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.median").withValue(0.001).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.p75").withValue(0.002).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.p95").withValue(0.003).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.p98").withValue(0.004).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.p99").withValue(0.005).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics.add(new MetricDatum().withMetricName("timer.p999").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));

        final List<MetricDatum> fixedPrecisionMetricData = request.getMetricData().stream()
                .map(FixPrecision.INSTANCE)
                .collect(Collectors.toList());

        assertThat(fixedPrecisionMetricData, containsInAnyOrder(expectedMetrics.toArray()));
    }

    @Test
    public void shouldReportTimersToCloudWatchWithCounterDifferential() {
        // Given
        final CloudWatchMetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
                .withClock(clock)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .build(cloudWatch, METRIC_NAMESPACE);

        given(timer.getCount()).willReturn(1L).willReturn(3L);
        given(timer.getMeanRate()).willReturn(2.0);
        given(timer.getOneMinuteRate()).willReturn(3.0);
        given(timer.getFiveMinuteRate()).willReturn(4.0);
        given(timer.getFifteenMinuteRate()).willReturn(5.0);
        given(timer.getSnapshot()).willReturn(snapshot);
        given(snapshot.getMax()).willReturn(6L);
        given(snapshot.getMin()).willReturn(7L);
        given(snapshot.getMean()).willReturn(8.0);
        given(snapshot.getStdDev()).willReturn(9.0);
        given(snapshot.getMedian()).willReturn(1.0);
        given(snapshot.get75thPercentile()).willReturn(2.0);
        given(snapshot.get95thPercentile()).willReturn(3.0);
        given(snapshot.get98thPercentile()).willReturn(4.0);
        given(snapshot.get99thPercentile()).willReturn(5.0);
        given(snapshot.get999thPercentile()).willReturn(6.0);

        final SortedMap<String, Gauge> gauges = Collections.emptySortedMap();
        final SortedMap<String, Counter> counters = Collections.emptySortedMap();
        final SortedMap<String, Histogram> histograms = Collections.emptySortedMap();
        final SortedMap<String, Meter> meters = Collections.emptySortedMap();
        final SortedMap<String, Timer> timers = new TreeMap<>();
        timers.put("timer", timer);

        final long timestamp = System.currentTimeMillis();
        given(clock.getTime()).willReturn(timestamp);

        given(cloudWatch.putMetricDataAsync(any(PutMetricDataRequest.class)))
                .willReturn(CompletableFuture.completedFuture(new PutMetricDataResult()));

        // When
        reporter.report(gauges, counters, histograms, meters, timers);
        reporter.report(gauges, counters, histograms, meters, timers);

        // Then
        final ArgumentCaptor<PutMetricDataRequest> requestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        then(cloudWatch).should(times(2)).putMetricDataAsync(requestCaptor.capture());

        final List<PutMetricDataRequest> requests = requestCaptor.getAllValues();

        assertThat(requests.get(0).getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics1 = new ArrayList<>();
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.count").withValue(1.0).withTimestamp(new Date(timestamp)));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.m1").withValue(3.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.m5").withValue(4.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.m15").withValue(5.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.max").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.min").withValue(0.007).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.mean").withValue(0.008).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.stddev").withValue(0.009).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.median").withValue(0.001).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.p75").withValue(0.002).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.p95").withValue(0.003).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.p98").withValue(0.004).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.p99").withValue(0.005).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics1.add(new MetricDatum().withMetricName("timer.p999").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));

        final List<MetricDatum> fixedPrecisionMetricData1 = requests.get(0).getMetricData().stream()
                .map(FixPrecision.INSTANCE)
                .collect(Collectors.toList());

        assertThat(fixedPrecisionMetricData1, containsInAnyOrder(expectedMetrics1.toArray()));

        assertThat(requests.get(1).getNamespace(), is(equalTo(METRIC_NAMESPACE)));

        final List<MetricDatum> expectedMetrics2 = new ArrayList<>();
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.count").withValue(2.0).withTimestamp(new Date(timestamp)));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.mean_rate").withValue(2.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.m1").withValue(3.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.m5").withValue(4.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.m15").withValue(5.0).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.None));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.max").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.min").withValue(0.007).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.mean").withValue(0.008).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.stddev").withValue(0.009).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.median").withValue(0.001).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.p75").withValue(0.002).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.p95").withValue(0.003).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.p98").withValue(0.004).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.p99").withValue(0.005).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));
        expectedMetrics2.add(new MetricDatum().withMetricName("timer.p999").withValue(0.006).withTimestamp(new Date(timestamp)).withUnit(StandardUnit.Microseconds));

        final List<MetricDatum> fixedPrecisionMetricData2 = requests.get(1).getMetricData().stream()
                .map(FixPrecision.INSTANCE)
                .collect(Collectors.toList());

        assertThat(fixedPrecisionMetricData2, containsInAnyOrder(expectedMetrics2.toArray()));
    }

    private enum FixPrecision implements Function<MetricDatum, MetricDatum> {
        INSTANCE;

        @Override
        public MetricDatum apply(MetricDatum metricDatum) {
            metricDatum.setValue(new BigDecimal(metricDatum.getValue()).setScale(3, RoundingMode.HALF_UP).doubleValue());
            return metricDatum;
        }
    }
}