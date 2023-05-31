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
package co.elastic.apm.agent.reactor.netty.httpclient.httpclient;

import co.elastic.apm.agent.reactor.netty.httpclient.DecoratorFunctions;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;

import java.util.function.BiConsumer;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class OnRequest extends AbstractNettyHttpClientInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return ElementMatchers
                .isPublic()
                .and(hasMethodName("doOnRequest"))
                .and(takesArguments(1))
                .and(takesArgument(0, BiConsumer.class));
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(value = 0, readOnly = false) BiConsumer<? super HttpClientRequest, ? super Connection> callback) {
            if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
                // perform the callback with the client span active (instead of the parent) since this
                // callback occurs after the connection is made
                callback = new DecoratorFunctions.OnMessageDecorator<>(callback, DecoratorFunctions.PropagatedSpan.CLIENT);
            }
        }
    }
}
