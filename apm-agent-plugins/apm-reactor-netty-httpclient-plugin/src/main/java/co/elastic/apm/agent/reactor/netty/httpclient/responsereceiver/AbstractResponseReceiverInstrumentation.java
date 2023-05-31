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


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.reactor.netty.httpclient.AbstractReactorNettyInstrumentation;
import co.elastic.apm.agent.reactor.netty.httpclient.utils.NettyHttpClientHeaderManipulator;
import co.elastic.apm.agent.reactor.netty.httpclient.utils.SpanHolder;
import co.elastic.apm.agent.reactor.netty.httpclient.utils.Utils;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static co.elastic.apm.agent.reactor.netty.httpclient.ReactorContextKeys.CLIENT_PARENT_SPAN_KEY;
import static co.elastic.apm.agent.reactor.netty.httpclient.ReactorContextKeys.CLIENT_SPAN_KEY;

public abstract class AbstractResponseReceiverInstrumentation extends AbstractReactorNettyInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return ElementMatchers.named("reactor.netty.http.client.HttpClientFinalizer");
    }

    // this method adds several stateful listeners that execute the instrumenter lifecycle during HTTP
    // request processing
    // it should be used just before one of the response*() methods is called - after this point the
    // HTTP
    // request is no longer modifiable by the user
    public static HttpClient.ResponseReceiver<?> instrument(HttpClient.ResponseReceiver<?> receiver) {
        // receiver should always be an HttpClientFinalizer, which both extends HttpClient and
        // implements ResponseReceiver
        if (receiver instanceof HttpClient) {
            HttpClient client = (HttpClient) receiver;
            HttpClientConfig config = client.configuration();

            SpanHolder spanHolder = new SpanHolder();

            HttpClient modified =
                client
                    .mapConnect(new StartOperation(spanHolder, config))
                    .doOnRequest(new PropagateContext(spanHolder))
                    .doOnRequestError(new EndOperationWithRequestError(spanHolder, config))
                    .doOnResponseError(new EndOperationWithResponseError(spanHolder, config))
                    .doAfterResponseSuccess(new EndOperationWithSuccess(spanHolder, config));

            // modified should always be an HttpClientFinalizer too
            if (modified instanceof HttpClient.ResponseReceiver) {
                return (HttpClient.ResponseReceiver<?>) modified;
            }
        }

        return null;
    }

    /**
     * Operation that gets executed when mapConnect is called on the HTTPClient
     * It starts the child span and saves them in the span holder.
     */
    static final class StartOperation implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

        private final SpanHolder spanHolder;
        private final HttpClientConfig config;

        StartOperation(SpanHolder spanHolder, HttpClientConfig config) {
            this.spanHolder = spanHolder;
            this.config = config;
        }

        @Override
        public Mono<? extends Connection> apply(Mono<? extends Connection> mono) {
            return Mono.defer(
                    () -> {
                        Tracer tracer = Utils.getTracer();
                        AbstractSpan<?> parentSpan = tracer.getActive();

                        if (parentSpan == null) {
                            return mono;
                        }

                        spanHolder.setParentSpan(parentSpan);
                        Span<?> childSpan = HttpClientHelper.startHttpClientSpan(parentSpan, config.method().toString(), URI.create(config.uri()), null);

                        if (childSpan != null) {
                            spanHolder.setChildSpan(childSpan);
                            childSpan.activate();
                        }

                        return mono.contextWrite(ctx -> {
                            ctx.put(CLIENT_PARENT_SPAN_KEY, parentSpan);
                            if (childSpan != null) {
                                ctx.put(CLIENT_SPAN_KEY, childSpan);
                            }
                            return ctx;
                        });
                    })
                .doOnCancel(
                    () -> {
                        Utils.endSpan(spanHolder, config, null, null, null);
                    });
        }
    }

    static final class PropagateContext implements BiConsumer<HttpClientRequest, Connection> {

        private final SpanHolder spanHolder;

        PropagateContext(SpanHolder spanHolder) {
            this.spanHolder = spanHolder;
        }


        @Override
        public void accept(HttpClientRequest httpClientRequest, Connection connection) {
            Tracer tracer = Utils.getTracer();
            if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), httpClientRequest, new NettyHttpClientHeaderManipulator())) {
                Span<?> childSpan = spanHolder.getChildSpan();
                if (childSpan != null) {
                    childSpan.propagateTraceContext(httpClientRequest, new NettyHttpClientHeaderManipulator());
                } else {
                    AbstractSpan<?> parentSpan = spanHolder.getParentSpan();
                    parentSpan.propagateTraceContext(httpClientRequest, new NettyHttpClientHeaderManipulator());
                }
            }
        }
    }

    static final class EndOperationWithRequestError implements BiConsumer<HttpClientRequest, Throwable> {

        private final SpanHolder spanHolder;
        private final HttpClientConfig config;

        EndOperationWithRequestError(SpanHolder spanHolder, HttpClientConfig config) {
            this.spanHolder = spanHolder;
            this.config = config;
        }

        @Override
        public void accept(HttpClientRequest httpClientRequest, Throwable throwable) {
            Utils.endSpan(spanHolder, config, httpClientRequest, null, throwable);
        }
    }

    static final class EndOperationWithResponseError implements BiConsumer<HttpClientResponse, Throwable> {

        private final SpanHolder spanHolder;
        private final HttpClientConfig config;

        EndOperationWithResponseError(SpanHolder spanHolder, HttpClientConfig config) {
            this.spanHolder = spanHolder;
            this.config = config;
        }

        @Override
        public void accept(HttpClientResponse response, Throwable error) {
            Utils.endSpan(spanHolder, config, null, response, error);
        }
    }

    static final class EndOperationWithSuccess implements BiConsumer<HttpClientResponse, Connection> {

        private final SpanHolder spanHolder;
        private final HttpClientConfig config;

        EndOperationWithSuccess(SpanHolder spanHolder, HttpClientConfig config) {
            this.spanHolder = spanHolder;
            this.config = config;
        }

        @Override
        public void accept(HttpClientResponse response, Connection connection) {
            Utils.endSpan(spanHolder, config, null, response, null);
        }
    }

}
