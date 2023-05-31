/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.reactor.netty.httpclient.responsereceiver;

import co.elastic.apm.agent.sdk.state.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ResponseMonoInstrumentation extends AbstractResponseReceiverInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return ElementMatchers.named("response").and(takesArguments(0)).and(returns(named("reactor.core.publisher.Mono")));
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static HttpClient.ResponseReceiver<?> onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
            CallDepth callDepth = CallDepth.get(HttpClient.ResponseReceiver.class);
            if (callDepth.isNestedCallAndIncrement()) {
                // Null return value will execute the original method when nested.
                return null;
            }

            // Non-Null return value will skip the original method invocation
            return instrument(receiver);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToReturned
        public static Mono<HttpClientResponse> onExit(
            @Advice.Enter HttpClient.ResponseReceiver<?> modifiedReceiver,
            @Advice.Return Mono<HttpClientResponse> originalReturn) {

            try {
                if (modifiedReceiver != null) {
                    return modifiedReceiver.response();
                } else {
                    return originalReturn;
                }
            } finally {
                CallDepth callDepth = CallDepth.get(HttpClient.ResponseReceiver.class);
                callDepth.decrement();
            }
        }
    }
}
