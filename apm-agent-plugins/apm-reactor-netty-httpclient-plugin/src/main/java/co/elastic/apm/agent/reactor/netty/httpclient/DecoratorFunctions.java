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

import co.elastic.apm.agent.impl.transaction.Span;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientInfos;
import reactor.util.annotation.Nullable;
import reactor.util.context.ContextView;

import java.util.function.BiConsumer;

public class DecoratorFunctions {

    public static boolean shouldDecorate(Class<?> callbackClass) {
        return callbackClass != OnMessageDecorator.class && callbackClass != OnMessageErrorDecorator.class;
    }

    public static final class OnMessageDecorator<M extends HttpClientInfos>
            implements BiConsumer<M, Connection> {

        private final BiConsumer<? super M, ? super Connection> delegate;
        private final PropagatedSpan propagatedSpan;

        public OnMessageDecorator(
                BiConsumer<? super M, ? super Connection> delegate, PropagatedSpan propagatedSpan) {
            this.delegate = delegate;
            this.propagatedSpan = propagatedSpan;
        }

        @Override
        public void accept(M message, Connection connection) {
            Span span = getChannelSpan(message.currentContextView(), propagatedSpan);
            if (span == null) {
                delegate.accept(message, connection);
            } else {
//                try (Span ignored = span.activate()) {
//                    delegate.accept(message, connection);
//                }
            }
        }
    }

    public static final class OnMessageErrorDecorator<M extends HttpClientInfos> implements BiConsumer<M, Throwable> {

        private final BiConsumer<? super M, ? super Throwable> delegate;
        private final PropagatedSpan propagatedSpan;

        public OnMessageErrorDecorator(BiConsumer<? super M, ? super Throwable> delegate, PropagatedSpan propagatedSpan) {
            this.delegate = delegate;
            this.propagatedSpan = propagatedSpan;
        }

        @Override
        public void accept(M message, Throwable throwable) {
            Span span = getChannelSpan(message.currentContextView(), propagatedSpan);
            if (span == null) {
                delegate.accept(message, throwable);
            } else {
//                try (Scope ignored = span.activate()) {
//                    delegate.accept(message, throwable);
//                }
            }
        }
    }

    @Nullable
    private static Span getChannelSpan(ContextView contextView, PropagatedSpan propagatedSpan) {
        Span span = null;
        if (propagatedSpan.useClientSpan) {
            span = contextView.getOrDefault(ReactorContextKeys.CLIENT_SPAN_KEY, null);
        }
        if (span == null) {
            span = contextView.getOrDefault(ReactorContextKeys.CLIENT_PARENT_SPAN_KEY, null);
        }
        return span;
    }

    public enum PropagatedSpan {
        PARENT(false),
        CLIENT(true);

        final boolean useClientSpan;

        PropagatedSpan(boolean useClientSpan) {
            this.useClientSpan = useClientSpan;
        }
    }

    private DecoratorFunctions() {}
}
