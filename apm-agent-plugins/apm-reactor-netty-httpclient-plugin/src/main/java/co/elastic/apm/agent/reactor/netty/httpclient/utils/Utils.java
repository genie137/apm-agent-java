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
package co.elastic.apm.agent.reactor.netty.httpclient.utils;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.metadata.Http;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public class Utils {

    public static Tracer getTracer() {
        return GlobalTracer.get();
    }

    public static void endSpan(SpanHolder spanHolder, HttpClientConfig config, HttpClientRequest request, HttpClientResponse response, Throwable throwable) {
        Span<?> span = spanHolder.getAndRemoveChildSpan();
        AbstractSpan<?> parentSpan = spanHolder.getAndRemoveParentSpan();

        if (span == null) {
            return;
        }


        if (response != null) {
            updateSpanWithResponse(span, response);
        }

        if (throwable != null) {
            span.captureException(throwable);
        }

        System.out.println("PRE DEACTIVATE");
//        span.deactivate();
        span.end();
        System.out.println("POST DEACTIVATE");

    }

    public static void updateSpanWithResponse(Span<?> span, HttpClientResponse response) {
        Http httpContext = span.getContext().getHttp();
        httpContext.withStatusCode(response.status().code());
    }
}
