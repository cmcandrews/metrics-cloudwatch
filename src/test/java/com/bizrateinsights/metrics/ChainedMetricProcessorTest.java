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
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;

/**
 * @author Chris McAndrews
 */
@RunWith(MockitoJUnitRunner.class)
public class ChainedMetricProcessorTest {

    @Mock
    private Function<MetricDatum, MetricDatum> delegate;

    private ChainedMetricProcessor processor;

    @Test
    public void shouldReturnMutatedMetric() {
        // Given
        processor = ChainedMetricProcessor.using(ImmutableList.of(delegate));
        final MetricDatum original = new MetricDatum().withMetricName("original");
        final MetricDatum mutated = new MetricDatum().withMetricName("mutated");
        given(delegate.apply(any(MetricDatum.class))).willReturn(mutated);

        // When
        final MetricDatum result = processor.apply(original);

        // Then
        assertThat(result, is(equalTo(mutated)));
        then(delegate).should().apply(original);
    }

    @Test
    public void shouldReturnOriginalForEmptyDelegateList() {
        // Given
        processor = ChainedMetricProcessor.using(ImmutableList.<Function<MetricDatum, MetricDatum>>of());
        final MetricDatum original = new MetricDatum().withMetricName("original");

        // When
        final MetricDatum result = processor.apply(original);

        // Then
        assertThat(result, is(equalTo(original)));
    }
}