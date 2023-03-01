/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.apm.agent.reactor.netty.httpclient;

import co.elastic.apm.agent.impl.transaction.Span;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HttpResponseReceiverInstrument {

  // this method adds several stateful listeners that execute the instrumenter lifecycle during HTTP
  // request processing
  // it should be used just before one of the response*() methods is called - after this point the
  // HTTP
  // request is no longer modifiable by the user
  @Nullable
  public static HttpClient.ResponseReceiver<?> instrument(HttpClient.ResponseReceiver<?> receiver) {
    // receiver should always be an HttpClientFinalizer, which both extends HttpClient and
    // implements ResponseReceiver
    if (receiver instanceof HttpClient) {
      HttpClient client = (HttpClient) receiver;
      HttpClientConfig config = client.configuration();

      ContextHolder contextHolder = new ContextHolder();

      HttpClient modified =
          client
              .mapConnect(new StartOperation(contextHolder, config))
              .doOnRequest(new PropagateContext(contextHolder))
              .doOnRequestError(new EndOperationWithRequestError(contextHolder, config))
              .doOnResponseError(new EndOperationWithResponseError(contextHolder, config))
              .doAfterResponseSuccess(new EndOperationWithSuccess(contextHolder, config));

      // modified should always be an HttpClientFinalizer too
      if (modified instanceof HttpClient.ResponseReceiver) {
        return (HttpClient.ResponseReceiver<?>) modified;
      }
    }

    return null;
  }

  static final class ContextHolder {

    private static final AtomicReferenceFieldUpdater<ContextHolder, Span> contextUpdater = AtomicReferenceFieldUpdater.newUpdater(ContextHolder.class, Span.class, "span");

    volatile Span parentContext;
    volatile Span span;

    void setContext(Span span) {
      contextUpdater.set(this, span);
    }

    Span getAndRemoveContext() {
      return contextUpdater.getAndSet(this, null);
    }
  }

  static final class StartOperation
      implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    StartOperation(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public Mono<? extends Connection> apply(Mono<? extends Connection> mono) {
      return Mono.defer(
              () -> {
                Span parentContext = Span.current();
                contextHolder.parentContext = parentContext;
                if (!instrumenter().shouldStart(parentContext, config)) {
                  // make span accessible via the reactor ContextView - the doOn* callbacks
                  // instrumentation uses this to set the proper span for callbacks
                  return mono.contextWrite(
                      ctx -> ctx.put(CLIENT_PARENT_CONTEXT_KEY, parentContext));
                }

                Span span = instrumenter().start(parentContext, config);
                contextHolder.setContext(span);
                return ContextPropagationOperator.runWithContext(mono, span)
                    // make contexts accessible via the reactor ContextView - the doOn* callbacks
                    // instrumentation uses the parent span to set the proper span for
                    // callbacks
                    .contextWrite(ctx -> ctx.put(CLIENT_PARENT_CONTEXT_KEY, parentContext))
                    .contextWrite(ctx -> ctx.put(CLIENT_CONTEXT_KEY, span));
              })
          .doOnCancel(
              () -> {
                Span span = contextHolder.getAndRemoveContext();
                if (span == null) {
                  return;
                }
                instrumenter().end(span, config, null, null);
              });
    }
  }

  static final class PropagateContext implements BiConsumer<HttpClientRequest, Connection> {

    private final ContextHolder contextHolder;

    PropagateContext(ContextHolder contextHolder) {
      this.contextHolder = contextHolder;
    }

    @Override
    public void accept(HttpClientRequest httpClientRequest, Connection connection) {
      Span span = contextHolder.span;
      if (span != null) {
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(span, httpClientRequest, HttpClientRequestHeadersSetter.INSTANCE);
      }

      // also propagate the span to the underlying netty instrumentation
      // if this span was suppressed and span is null, propagate parentContext - this will allow
      // netty spans to be suppressed too
      Span nettyParentContext = span == null ? contextHolder.parentContext : span;
      NettyClientTelemetry.setChannelContext(connection.channel(), nettyParentContext);
    }
  }

  static final class EndOperationWithRequestError
      implements BiConsumer<HttpClientRequest, Throwable> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    EndOperationWithRequestError(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public void accept(HttpClientRequest httpClientRequest, Throwable error) {
      Span span = contextHolder.getAndRemoveContext();
      if (span == null) {
        return;
      }
      instrumenter().end(span, config, null, error);
    }
  }

  static final class EndOperationWithResponseError
      implements BiConsumer<HttpClientResponse, Throwable> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    EndOperationWithResponseError(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public void accept(HttpClientResponse response, Throwable error) {
      Span span = contextHolder.getAndRemoveContext();
      if (span == null) {
        return;
      }
      instrumenter().end(span, config, response, error);
    }
  }

  static final class EndOperationWithSuccess implements BiConsumer<HttpClientResponse, Connection> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    EndOperationWithSuccess(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public void accept(HttpClientResponse response, Connection connection) {
      Span span = contextHolder.getAndRemoveContext();
      if (span == null) {
        return;
      }
      instrumenter().end(span, config, response, null);
    }
  }

  private HttpResponseReceiverInstrument() {}
}
