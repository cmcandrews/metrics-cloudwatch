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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Chris McAndrews
 */
public class TagToDimensionTest {

    @Test
    public void shouldConvertTagToDimension() {
        // Given
        final Map<String, String> tags = new HashMap<>();
        tags.put("key", "value");

        // When
        final Dimension result = TagToDimension.INSTANCE.apply(tags.entrySet().stream().findFirst().get());

        // Then
        final Dimension expected = new Dimension().withName("key").withValue("value");
        assertThat(result, is(equalTo(expected)));
    }
}