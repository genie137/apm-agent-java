package co.elastic.apm.agent.reactor.netty.httpclient;

import co.elastic.apm.agent.sdk.state.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ReactorNettyResponseReceiverMonoInstrumentation extends AbstractReactorNettyHttpClientInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return
            named("response")
                .and(takesArguments(0))
                .and(returns(named("reactor.core.publisher.Mono")));
    }

    @SuppressWarnings("Unused")
    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
        public static HttpClient.ResponseReceiver<?> onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
            CallDepth callDepth = CallDepth.get(AdviceClass.class);
            if (callDepth.isNestedCallAndIncrement()) {
                // execute the original method on nested calls
                return null;
            }

            // non-null value will skip the original method invocation
            return HttpResponseReceiverInstrument.instrument(receiver);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.Enter HttpClient.ResponseReceiver<?> modifiedReceiver, @Advice.Return(readOnly = false) Mono<HttpClientResponse> returnValue) {
            CallDepth callDepth = CallDepth.get(AdviceClass.class);

            try {
                if (modifiedReceiver != null) {
                    returnValue = modifiedReceiver.response();
                }
            } finally {
                // needs to be called after original method to prevent StackOverflowError
                callDepth.decrement();
            }
        }
    }
}
