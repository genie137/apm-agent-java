package co.elastic.apm.agent.reactor.netty.httpclient;

import co.elastic.apm.agent.sdk.state.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.util.function.BiFunction;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ReactorNettyResponseReceiverFluxInstrumentation extends AbstractReactorNettyHttpClientInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return
            named("response")
                .and(takesArguments(1))
                .and(takesArgument(0, BiFunction.class))
                .and(returns(named("reactor.core.publisher.Flux")));
    }

    @SuppressWarnings("Unused")
    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
        public static HttpClient.ResponseReceiver<?> onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {

            callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
            if (callDepth.getAndIncrement() > 0) {
                // execute the original method on nested calls
                return null;
            }

            // non-null value will skip the original method invocation
            return HttpResponseReceiverInstrumenter.instrument(receiver);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static <T extends HttpClient.ResponseReceiver<?>> void onExit(
            @Advice.Enter HttpClient.ResponseReceiver<T> modifiedReceiver,
            @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super ByteBufFlux, ? extends Publisher<T>> receiveFunction,
            @Advice.Return(readOnly = false) Flux<?> returnValue) {

            try {
                if (modifiedReceiver != null) {
                    returnValue = modifiedReceiver.response(receiveFunction);
                }
            } finally {
                // needs to be called after original method to prevent StackOverflowError
                callDepth.decrementAndGet();
            }
        }
    }
}
