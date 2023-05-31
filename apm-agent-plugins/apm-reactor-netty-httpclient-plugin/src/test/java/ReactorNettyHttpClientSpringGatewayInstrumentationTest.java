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

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.Before;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Tests using the default settings from Spring Gateway as defined in org.springframework.cloud.gateway.config.HttpClientFactory;
 */
public class ReactorNettyHttpClientSpringGatewayInstrumentationTest extends AbstractHttpClientInstrumentationTest {
    private HttpClient client;

    @Before
    public void setUp() {
        client = buildHttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        client
            .followRedirect(true)
            .get()
            .uri(path)
            .responseSingle((response, byteBufMono) -> {
                // Calling this causes the span to end.
                return byteBufMono.map(unused -> response);
            })
            .block();
    }

    @Override
    protected boolean isErrorOnCircularRedirectSupported() {
        return false;
    }

    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return false;
    }

    protected HttpClient buildHttpClient() {
        ConnectionProvider connectionProvider = buildConnectionprovider();
        HttpClient httpClient = HttpClient.create(connectionProvider);
        httpClient.wiretap(true);
        httpClient.followRedirect(true);
        return httpClient;
    }

    protected ConnectionProvider buildConnectionprovider() {
        ConnectionProvider.Builder builder = ConnectionProvider.builder("proxy");
        builder.maxConnections(Integer.MAX_VALUE);
        builder.pendingAcquireTimeout(Duration.ofMillis(0));
        builder.pendingAcquireMaxCount(-1);
        builder.evictInBackground(Duration.ZERO);
        builder.metrics(false);
        return builder.build();
    }

}
