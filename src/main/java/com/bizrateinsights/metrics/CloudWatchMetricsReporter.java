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
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Chris McAndrews
 */
public class CloudWatchMetricsReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchMetricsReporter.class);

    private final AmazonCloudWatchAsyncClient cloudWatch;
    private final String metricNamespace;
    private final Clock clock;
    private final String prefix;
    private final Map<String, String> tags;

    /**
     * We only submit the difference in counters since the last submission. This way we don't have to reset the counters
     * within this application.
     */
    private final Map<Counting, Long> lastPolledCounts = new HashMap<>();

    private final boolean enabled;

    private boolean decorateCounters = true;
    private boolean decorateGauges = true;

    private Predicate<MetricDatum> metricDatumFilter = x -> true;
    private List<Function<MetricDatum, MetricDatum>> cloudWatchMetricProcessors = Collections.emptyList();
    private List<Function<MetricDatum, Set<MetricDatum>>> duplicatingCloudWatchMetricProcessors = Collections.emptyList();

    /**
     * Returns a new {@link Builder} for {@link CloudWatchMetricsReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link CloudWatchMetricsReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    /**
     * A builder for {@link CloudWatchMetricsReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Predicate<MetricDatum> metricDatumFilter = x -> true;
        private Map<String, String> tags = Collections.emptyMap();
        private boolean decorateCounters;
        private boolean decorateGauges;
        private boolean enabled;
        private List<Function<MetricDatum, MetricDatum>> cloudWatchMetricProcessors = Collections.emptyList();
        private List<Function<MetricDatum, Set<MetricDatum>>> duplicatingCloudWatchMetricProcessors = Collections.emptyList();

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.clock = Clock.defaultClock();
            this.prefix = "";
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.decorateCounters = true;
            this.decorateGauges = true;
            this.enabled = true;
        }

        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Only report specific snapshot data for a metric that matches the given filter.
         * E.g. used to filter out things such as 'mean' from a timer while keeping 'count'.
         *
         * @param metricDatumFilter a {@link Predicate} with snapshot data to keep
         * @return
         */
        public Builder metricDatumFilter(Predicate<MetricDatum> metricDatumFilter) {
            this.metricDatumFilter = metricDatumFilter;
            return this;
        }

        /**
         * Applies a list of functions to mutate metrics before they are sent to CloudWatch.  Useful for tasks such as
         * renaming vaguely-named metrics.
         *
         * @param cloudWatchMetricProcessors
         * @return
         */
        public Builder cloudWatchMetricProcessors(List<Function<MetricDatum, MetricDatum>> cloudWatchMetricProcessors) {
            this.cloudWatchMetricProcessors = Collections.unmodifiableList(cloudWatchMetricProcessors);
            return this;
        }

        /**
         * Applies a list of functions that will mutate metrics before they are sent to CloudWatch, while keeping the
         * original metric intact.  Useful for sending duplicate metrics to CloudWatch tagged with different dimensions,
         * since CloudWatch is not capable of querying on wildcard tags.
         *
         * @param duplicatingCloudWatchMetricProcessors
         * @return
         */
        public Builder withDuplicatingCloudWatchMetricProcessors(List<Function<MetricDatum, Set<MetricDatum>>> duplicatingCloudWatchMetricProcessors) {
            this.duplicatingCloudWatchMetricProcessors = Collections.unmodifiableList(duplicatingCloudWatchMetricProcessors);
            return this;
        }

        /**
         * Append tags to all reported metrics
         *
         * @param tags
         * @return {@code this}
         */
        public Builder withTags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Enables/disables all API calls to CloudWatch (for debugging purposes, or to avoid collecting metrics for
         * non-production environments).
         *
         * @param enabled
         * @return {@code this}
         */
        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Enable decorating Counter metric names with {@code .count} and Gauge metric names with
         * {@code .value}.
         *
         * @param withCounterGaugeDecorations
         * @return {@code this}
         */
        public Builder withCounterGaugeDecorations(boolean withCounterGaugeDecorations) {
            this.decorateCounters = withCounterGaugeDecorations;
            this.decorateGauges = withCounterGaugeDecorations;
            return this;
        }

        /**
         * Builds a {@link CloudWatchMetricsReporter} with the given properties, sending metrics using the
         * given {@link AmazonCloudWatchAsyncClient} client and namespace.
         *
         * @param cloudWatch a {@link AmazonCloudWatchAsyncClient} client
         * @param metricNamespace the CloudWatch metrics namespace
         * @return a {@link CloudWatchMetricsReporter}
         */
        public CloudWatchMetricsReporter build(AmazonCloudWatchAsyncClient cloudWatch, String metricNamespace) {
            return new CloudWatchMetricsReporter(registry,
                    cloudWatch,
                    metricNamespace,
                    clock,
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter,
                    metricDatumFilter,
                    cloudWatchMetricProcessors,
                    duplicatingCloudWatchMetricProcessors,
                    tags,
                    decorateCounters,
                    decorateGauges,
                    enabled);
        }
    }

    private CloudWatchMetricsReporter(MetricRegistry registry,
                                      AmazonCloudWatchAsyncClient cloudWatch,
                                      String metricNamespace,
                                      Clock clock,
                                      String prefix,
                                      TimeUnit rateUnit,
                                      TimeUnit durationUnit,
                                      MetricFilter filter,
                                      Predicate<MetricDatum> metricDatumFilter,
                                      List<Function<MetricDatum, MetricDatum>> cloudWatchMetricProcessors,
                                      List<Function<MetricDatum, Set<MetricDatum>>> duplicatingCloudWatchMetricProcessors,
                                      Map<String, String> tags,
                                      boolean decorateCounters,
                                      boolean decorateGauges,
                                      boolean enabled) {

        super(registry, "cloudwatch-reporter", filter, rateUnit, durationUnit);
        this.cloudWatch = cloudWatch;
        this.metricNamespace = metricNamespace;
        this.clock = clock;
        this.prefix = prefix;
        this.tags = tags;
        this.decorateCounters = decorateCounters;
        this.decorateGauges = decorateGauges;
        this.enabled = enabled;
        this.metricDatumFilter = metricDatumFilter;
        this.cloudWatchMetricProcessors = Collections.unmodifiableList(cloudWatchMetricProcessors);
        this.duplicatingCloudWatchMetricProcessors = Collections.unmodifiableList(duplicatingCloudWatchMetricProcessors);
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {

        try {
            final long timestamp = clock.getTime();

            final Set<MetricDatum> metrics = new HashSet<>();

            for (Map.Entry<String, Gauge> g : gauges.entrySet()) {

                // Make sure the gauge value is numeric.
                final Optional<Double> value = parseDouble(g.getValue().getValue());
                if (!value.isPresent()) {
                    continue;
                }

                Map<String, String> tagsToUse = new HashMap<>(tags);
                String key = g.getKey();
                metrics.add(buildGauge(key, value.get(), timestamp, tagsToUse));
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                Map<String, String> tagsToUse = new HashMap<>(tags);
                String key = entry.getKey();
                metrics.add(buildCounter(key, entry.getValue(), timestamp, tagsToUse));
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                Map<String, String> tagsToUse = new HashMap<>(tags);
                String key = entry.getKey();
                metrics.addAll(buildHistograms(key, entry.getValue(), timestamp, tagsToUse));
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                Map<String, String> tagsToUse = new HashMap<>(tags);
                String key = entry.getKey();
                metrics.addAll(buildMeters(key, entry.getValue(), timestamp, tagsToUse));
            }

            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                Map<String, String> tagsToUse = new HashMap<>(tags);
                String key = entry.getKey();
                metrics.addAll(buildTimers(key, entry.getValue(), timestamp, tagsToUse));
            }

            // Apply metric datum filter and processors
            final Set<MetricDatum> filteredAndProcessed = metrics.stream()
                    .filter(metricDatumFilter)
                    .map(ChainedMetricProcessor.using(cloudWatchMetricProcessors))
                    .flatMap(ChainedDuplicatingMetricProcessor.using(duplicatingCloudWatchMetricProcessors))
                    .collect(Collectors.toSet());

            // Each CloudWatch API request may contain at maximum 20 datums. Break into partitions of 20.
            final AtomicInteger counter = new AtomicInteger();
            List<List<MetricDatum>> dataPartitions = new ArrayList<>(filteredAndProcessed.stream()
                    .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / 20))
                    .values());
            List<Future<?>> cloudWatchFutures = new ArrayList<>();

            if (enabled) {
                // Submit asynchronously with threads.
                for (List<MetricDatum> dataSubset : dataPartitions) {
                    cloudWatchFutures.add(cloudWatch.putMetricDataAsync(new PutMetricDataRequest()
                            .withNamespace(metricNamespace)
                            .withMetricData(dataSubset)));
                }

                // Wait for CloudWatch putMetricData futures to be fulfilled.
                for (Future<?> cloudWatchFuture : cloudWatchFutures) {
                    // We can't let an exception leak out of here, or else the reporter will cease running as described in
                    // java.util.concurrent.ScheduledExecutorService.scheduleAtFixedRate(Runnable, long, long, TimeUnit unit)
                    try {
                        // See what happened in case of an error.
                        cloudWatchFuture.get();
                    } catch (Exception e) {
                        LOG.error("Exception reporting metrics to CloudWatch. The data in this CloudWatch API request " +
                                "may have been discarded, did not make it to CloudWatch.", e);
                    }
                }
            }

            LOG.debug("Sent {} metric data to CloudWatch. namespace: {}", filteredAndProcessed.size(), metricNamespace);

            if (!enabled) {
                LOG.warn("CloudWatch API calls are currently DISABLED for this application instance.");
            }

            for (MetricDatum metricDatum : filteredAndProcessed) {
                LOG.debug("CloudWatch metric: {}", metricDatum);
            }

        } catch (RuntimeException e) {
            LOG.error("Error marshalling CloudWatch metrics.", e);
        }
    }

    private Set<MetricDatum> buildTimers(String name, Timer timer, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);
        final Snapshot snapshot = timer.getSnapshot();

        return collector.addMetric("count", diffLast(timer))
                //convert rate
                .addMetric("m15", convertRate(timer.getFifteenMinuteRate()), getRateUnit())
                .addMetric("m5", convertRate(timer.getFiveMinuteRate()), getRateUnit())
                .addMetric("m1", convertRate(timer.getOneMinuteRate()), getRateUnit())
                .addMetric("mean_rate", convertRate(timer.getMeanRate()), getRateUnit())
                // convert duration
                .addMetric("max", convertDuration(snapshot.getMax()), getDurationUnit())
                .addMetric("min", convertDuration(snapshot.getMin()), getDurationUnit())
                .addMetric("mean", convertDuration(snapshot.getMean()), getDurationUnit())
                .addMetric("stddev", convertDuration(snapshot.getStdDev()), getDurationUnit())
                .addMetric("median", convertDuration(snapshot.getMedian()), getDurationUnit())
                .addMetric("p75", convertDuration(snapshot.get75thPercentile()), getDurationUnit())
                .addMetric("p95", convertDuration(snapshot.get95thPercentile()), getDurationUnit())
                .addMetric("p98", convertDuration(snapshot.get98thPercentile()), getDurationUnit())
                .addMetric("p99", convertDuration(snapshot.get99thPercentile()), getDurationUnit())
                .addMetric("p999", convertDuration(snapshot.get999thPercentile()), getDurationUnit())
                .build();
    }

    private Set<MetricDatum> buildHistograms(String name, Histogram histogram, long timestamp, Map<String, String> tags) {

        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);
        final Snapshot snapshot = histogram.getSnapshot();

        return collector.addMetric("count", diffLast(histogram))
                .addMetric("max", snapshot.getMax())
                .addMetric("min", snapshot.getMin())
                .addMetric("mean", snapshot.getMean())
                .addMetric("stddev", snapshot.getStdDev())
                .addMetric("median", snapshot.getMedian())
                .addMetric("p75", snapshot.get75thPercentile())
                .addMetric("p95", snapshot.get95thPercentile())
                .addMetric("p98", snapshot.get98thPercentile())
                .addMetric("p99", snapshot.get99thPercentile())
                .addMetric("p999", snapshot.get999thPercentile())
                .build();
    }

    private Set<MetricDatum> buildMeters(String name, Meter meter, long timestamp, Map<String, String> tags) {

        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);

        return collector.addMetric("count", diffLast(meter))
                // convert rate
                .addMetric("mean_rate", convertRate(meter.getMeanRate()))
                .addMetric("m1", convertRate(meter.getOneMinuteRate()))
                .addMetric("m5", convertRate(meter.getFiveMinuteRate()))
                .addMetric("m15", convertRate(meter.getFifteenMinuteRate()))
                .build();
    }

    private MetricDatum buildCounter(String name, Counter counter, long timestamp, Map<String, String> tags) {
        return new MetricDatum().withMetricName(decorateCounters ? prefix(name, "count") : prefix(name))
                .withTimestamp(new Date(timestamp))
                .withValue(Long.valueOf(counter.getCount()).doubleValue())
                .withDimensions(tags.entrySet().stream().map(TagToDimension.INSTANCE).collect(Collectors.toSet()));
    }

    private MetricDatum buildGauge(String name, double value, long timestamp, Map<String, String> tags) {
        return new MetricDatum().withMetricName(decorateGauges ? prefix(name, "value") : prefix(name))
                .withValue(value)
                .withTimestamp(new Date(timestamp))
                .withDimensions(tags.entrySet().stream().map(TagToDimension.INSTANCE).collect(Collectors.toSet()));
    }

    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }

    private long diffLast(Counting metric) {
        long count = metric.getCount();

        Long lastCount = lastPolledCounts.get(metric);
        lastPolledCounts.put(metric, count);

        if (lastCount == null) {
            lastCount = 0L;
        }
        return count - lastCount;
    }

    private Optional<Double> parseDouble(Object value) {
        try {
            return Optional.of(Double.parseDouble(value.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}