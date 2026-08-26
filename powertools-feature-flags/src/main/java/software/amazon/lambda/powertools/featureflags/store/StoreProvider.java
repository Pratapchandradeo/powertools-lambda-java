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

package software.amazon.lambda.powertools.featureflags.store;

import java.util.Map;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;

/**
 * Fetches a feature-flag configuration document.
 * <p>
 * The document is a JSON object whose keys are feature names. Implementations may
 * cache; this interface does not define cache semantics.
 */
public interface StoreProvider {

    /**
     * @return the raw feature-flag document (feature name → feature object)
     * @throws ConfigurationStoreException if the document cannot be retrieved
     */
    Map<String, Object> getConfiguration();
}
