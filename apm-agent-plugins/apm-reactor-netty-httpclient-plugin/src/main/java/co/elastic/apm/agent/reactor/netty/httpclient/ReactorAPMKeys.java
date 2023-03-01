/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.apm.agent.reactor.netty.httpclient;

public final class ReactorAPMKeys {
    public static final String APM_SPAN = ReactorAPMKeys.class.getName() + ".client-apm-span"; ;

    private ReactorAPMKeys() {}
}
