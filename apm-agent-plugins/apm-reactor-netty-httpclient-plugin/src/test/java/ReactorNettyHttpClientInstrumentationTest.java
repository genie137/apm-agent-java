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

/**
 * Tests using a simple implementation of the reactive netty HttpClient.
 */
public class ReactorNettyHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {
    private HttpClient client;

    @Before
    public void setUp() {
        client = HttpClient.create();
        client.doOnRequest((httpClientRequest, connection) -> {
            System.out.println("REQUEST");
        });
        client.doOnResponse((httpClientResponse, connection) -> {
            System.out.println("RESPONSE");
        });
        client.doOnError((httpClientRequest, throwable) -> {
            System.out.println("ERROR - REQ");
            throwable.printStackTrace();
        }, (httpClientResponse, throwable) -> {
            System.out.println("ERROR - RES");
            throwable.printStackTrace();
        });
    }

    @Override
    protected void performGet(String path) throws Exception {
        client
            .get()
            .uri(path)
            .response()
            .block();
    }

    @Override
    protected boolean isErrorOnCircularRedirectSupported() {
        // skip circular redirect test
        //
        // this http client just gives up after a fixed amount of redirects
        // this value can be set with the 'jdk.httpclient.redirects.retrylimit' and defaults to 5.
        // there is no exception thrown, the response provided to the user is just a redirect response
        return false;
    }

}
