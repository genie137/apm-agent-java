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
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

/**
 * Tests using a simple implementation of the reactive netty HttpClient.
 */
public class ReactorNettyHttpClientResponseConnectionTest extends AbstractHttpClientInstrumentationTest {
    private HttpClient client;

    @Before
    public void setUp() { client = buildHttpClient(); }

    @Override
    protected void performGet(String path) throws Exception {
        client
            .followRedirect(true)
            .get()
            .uri(path)
            .responseConnection((response, connection) -> Flux.just(connection))
            .blockFirst();
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
        HttpClient httpClient = HttpClient.create();
        httpClient.wiretap(true);
        return httpClient;
    }

}
