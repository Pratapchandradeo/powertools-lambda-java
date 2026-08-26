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

package software.amazon.lambda.powertools.logging.internal.handler;

import static java.lang.System.Logger.Level.INFO;
import static software.amazon.lambda.powertools.logging.argument.StructuredArguments.entry;

import org.slf4j.MDC;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.lambda.powertools.logging.Logging;

public class PowertoolsSystemLoggerHandler implements RequestHandler<Object, Object> {
    private static final System.Logger LOG = System.getLogger(PowertoolsSystemLoggerHandler.class.getName());

    @Override
    @Logging(clearState = true)
    public Object handleRequest(Object input, Context context) {
        MDC.put("cardNumber", "4111xxxx");
        LOG.log(INFO, "Processing order {0}", "order-1", entry("orderId", "order-1"));
        return "ok";
    }
}
