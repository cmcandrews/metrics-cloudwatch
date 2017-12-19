Metrics CloudWatch [![Build Status](https://travis-ci.org/bizrateinsights/metrics-cloudwatch.png?branch=master)](https://travis-ci.org/bizrateinsights/metrics-cloudwatch) [![Coverage Status](https://coveralls.io/repos/github/bizrateinsights/metrics-cloudwatch/badge.svg?branch=master)](https://coveralls.io/github/bizrateinsights/metrics-cloudwatch?branch=master)
==================
A DropWizard [Metrics](http://metrics.dropwizard.io/) Reporter.

This library allows your application to stream aggregated metric values to AWS CloudWatch.

Example Usage
-------------
```java
final AmazonCloudWatchAsyncClient client = new AmazonCloudWatchAsyncClient();
client.setRegion(Region.getRegion(awsRegion));

// You can add any dimensions that make sense for your specific use case.
// The reporter will add these dimensions to each metric sent to CloudWatch.
final Map<String, String> dimensions = ImmutableMap.of("environment", "dev");

final String cloudWatchNameSpace = "MyApplication";

final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate("metric-registry");

final MetricsReporter reporter = CloudWatchMetricsReporter.forRegistry(metricRegistry)
    .withTags(dimensions)
    .build(client, cloudWatchNameSpace);

final int reportingIntervalSeconds = 60;

reporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
```
