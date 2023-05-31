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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.util.function.BiFunction;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ResponseConnectionInstrumentation extends AbstractResponseReceiverInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return ElementMatchers
            .named("responseConnection")
            .and(takesArguments(1))
            .and(takesArgument(0, BiFunction.class))
            .and(returns(named("reactor.core.publisher.Flux")));
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
        public static <T extends HttpClient.ResponseReceiver<?>> Flux<?> onExit(
            @Advice.Enter HttpClient.ResponseReceiver<?> modifiedReceiver,
            @Advice.Argument(0) BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<T>> receiveFunction,
            @Advice.Return() Flux<?> originalReturn) {

            try {
                if (modifiedReceiver != null) {
                    return modifiedReceiver.responseConnection(receiveFunction);
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
