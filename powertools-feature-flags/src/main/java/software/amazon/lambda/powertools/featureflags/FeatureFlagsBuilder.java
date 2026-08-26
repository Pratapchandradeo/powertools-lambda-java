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

package software.amazon.lambda.powertools.featureflags;

import java.time.Clock;
import software.amazon.lambda.powertools.featureflags.store.StoreProvider;

/**
 * Builder for {@link FeatureFlags}.
 */
public final class FeatureFlagsBuilder {

    private StoreProvider store;
    private Clock clock = Clock.systemUTC();

    FeatureFlagsBuilder() {
    }

    /**
     * @param store the store used to fetch the feature-flag document (required)
     * @return this builder
     */
    public FeatureFlagsBuilder withStore(StoreProvider store) {
        this.store = store;
        return this;
    }

    /**
     * Override the clock used by time-based conditions. Defaults to UTC wall clock.
     * Primarily intended for tests.
     *
     * @param clock the clock to use
     * @return this builder
     */
    public FeatureFlagsBuilder withClock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * @return a configured {@link FeatureFlags}
     * @throws IllegalStateException if no store was provided
     */
    public FeatureFlags build() {
        if (store == null) {
            throw new IllegalStateException("No store provided; please provide one");
        }
        if (clock == null) {
            clock = Clock.systemUTC();
        }
        return new FeatureFlags(store, clock);
    }
}
