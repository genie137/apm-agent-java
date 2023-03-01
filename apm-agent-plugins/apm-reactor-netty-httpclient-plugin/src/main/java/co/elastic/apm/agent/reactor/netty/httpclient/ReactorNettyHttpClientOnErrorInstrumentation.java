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
package co.elastic.apm.agent.reactor.netty.httpclient;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import java.util.function.BiConsumer;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ReactorNettyHttpClientOnErrorInstrumentation extends AbstractReactorNettyHttpClientInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic()
            .and(named("doOnError")
                .and(takesArguments(2))
                .and(takesArgument(0, BiConsumer.class))
                .and(takesArgument(1, BiConsumer.class))
            );
    }

    @SuppressWarnings("unused")
    public static class AdviceClass {
        @net.bytebuddy.asm.Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@net.bytebuddy.asm.Advice.Argument(value = 0, readOnly = false) BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback, @net.bytebuddy.asm.Advice.Argument(value = 1, readOnly = false) BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback) {
            if (DecoratorFunctions.shouldDecorate(requestCallback.getClass())) {
                requestCallback = new DecoratorFunctions.OnMessageErrorDecorator<>(requestCallback);
            }
            if (DecoratorFunctions.shouldDecorate(responseCallback.getClass())) {
                responseCallback = new DecoratorFunctions.OnMessageErrorDecorator<>(responseCallback);
            }
        }
    }
}
