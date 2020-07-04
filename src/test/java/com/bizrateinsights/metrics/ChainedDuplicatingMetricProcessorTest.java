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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;

/**
 * @author Chris McAndrews
 */
@RunWith(MockitoJUnitRunner.class)
public class ChainedDuplicatingMetricProcessorTest {

    @Mock
    private Function<MetricDatum, Set<MetricDatum>> delegate;

    private ChainedDuplicatingMetricProcessor processor;

    @Test
    public void shouldReturnOriginalAndMutated() {
        // Given
        processor = ChainedDuplicatingMetricProcessor.using(ImmutableList.of(delegate));
        final MetricDatum original = new MetricDatum().withMetricName("metric1");
        final MetricDatum mutated = new MetricDatum().withMetricName("mutated");
        given(delegate.apply(any(MetricDatum.class))).willReturn(ImmutableSet.of(mutated));

        // When
        final Stream<MetricDatum> result = processor.apply(original);

        // Then
        assertThat(result.collect(Collectors.toSet()), containsInAnyOrder(original, mutated));
        then(delegate).should().apply(original);
    }

    @Test
    public void shouldReturnOriginalForEmptyDelegateList() {
        // Given
        processor =
                ChainedDuplicatingMetricProcessor.using(ImmutableList.of());

        final MetricDatum original = new MetricDatum().withMetricName("metric");

        // When
        final Stream<MetricDatum> result = processor.apply(original);

        // Then
        assertThat(result.collect(Collectors.toSet()), containsInAnyOrder(original));
    }
}