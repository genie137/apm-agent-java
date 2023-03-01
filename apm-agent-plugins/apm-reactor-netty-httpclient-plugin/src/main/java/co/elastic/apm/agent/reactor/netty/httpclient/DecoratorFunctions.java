package co.elastic.apm.agent.reactor.netty.httpclient;

import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientInfos;
import reactor.util.context.ContextView;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public final class DecoratorFunctions {

    // Test if the callback has already been decorated.
    public static boolean shouldDecorate(Class<?> callbackClass) {
        return callbackClass != OnMessageDecorator.class
            && callbackClass != OnMessageErrorDecorator.class;
    }

    public static final class OnMessageDecorator<M extends HttpClientInfos>
        implements BiConsumer<M, Connection> {

        private final BiConsumer<? super M, ? super Connection> delegate;
        public OnMessageDecorator(
            BiConsumer<? super M, ? super Connection> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(M message, Connection connection) {
            Span span = getChannelSpan(message.currentContextView());
            if (span == null) {
                delegate.accept(message, connection);
            } else {
                try (Scope ignored = span.activateInScope()) {
                    delegate.accept(message, connection);
                }
            }
        }
    }

    public static final class OnMessageErrorDecorator<M extends HttpClientInfos>
        implements BiConsumer<M, Throwable> {

        private final BiConsumer<? super M, ? super Throwable> delegate;

        public OnMessageErrorDecorator(BiConsumer<? super M, ? super Throwable> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(M message, Throwable throwable) {
            Span span = getChannelSpan(message.currentContextView());
            if (span == null) {
                delegate.accept(message, throwable);
            } else {
                try (Scope ignored = span.activateInScope()) {
                    delegate.accept(message, throwable);
                }
            }
        }
    }

    @Nullable
    private static Span getChannelSpan(ContextView contextView) {
        return contextView.getOrDefault(ReactorAPMKeys.APM_SPAN, null);
    }

}
