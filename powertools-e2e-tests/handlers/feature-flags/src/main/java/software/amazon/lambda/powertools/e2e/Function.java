/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package software.amazon.lambda.powertools.e2e;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.lambda.powertools.featureflags.FeatureFlags;
import software.amazon.lambda.powertools.featureflags.store.AppConfigStore;
import software.amazon.lambda.powertools.logging.Logging;

public class Function implements RequestHandler<Input, Map<String, Object>> {

    @Logging
    public Map<String, Object> handleRequest(Input input, Context context) {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(AppConfigStore.builder()
                        .withApplication(input.getApplication())
                        .withEnvironment(input.getEnvironment())
                        .withName(input.getName())
                        .build())
                .build();

        Map<String, Object> evaluationContext = input.getContext();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("premium_features", flags.evaluate("premium_features", evaluationContext, false));
        result.put("ten_percent_off_campaign",
                flags.evaluate("ten_percent_off_campaign", evaluationContext, false));
        result.put("enabled_features", flags.getEnabledFeatures(evaluationContext));
        return result;
    }
}
