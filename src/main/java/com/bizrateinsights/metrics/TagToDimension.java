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

import java.util.Map;
import java.util.function.Function;

/**
 * Converts a key/value tag to a CloudWatch dimension.
 *
 * @author Chris McAndrews
 */
public enum TagToDimension implements Function<Map.Entry<String, String>, Dimension> {
    INSTANCE;

    @Override
    public Dimension apply(Map.Entry<String, String> tag) {
        return new Dimension()
                .withName(tag.getKey())
                .withValue(tag.getValue());
    }
}