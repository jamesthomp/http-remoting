/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class SlashEncodingContractTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(SlashEncodingTestServer.class,
            "src/test/resources/test-server.yml");
    private static final FakeoInterface fakeoResource = mock(FakeoInterface.class);
    private static final String PATH_PARAM = "slash/path";
    private static final String QUERY_PARAM = "slash/query";

    @Rule
    public final MockWebServer server = new MockWebServer();

    private FakeoInterface jerseyProxy;
    private FakeoInterface inMemoryProxy;

    @Before
    public void before() {
        jerseyProxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + APP.getLocalPort()),
                FakeoInterface.class);
        inMemoryProxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server.getPort()),
                FakeoInterface.class);
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Test
    public void testJerseyDeocodesPathAndQueryParams() {
        jerseyProxy.encoded(PATH_PARAM, QUERY_PARAM);
        verify(fakeoResource).encoded(PATH_PARAM, QUERY_PARAM);
    }

    @Test
    public void testPathAndQueryParamsAreEncoded() throws InterruptedException {
        inMemoryProxy.encoded(PATH_PARAM, QUERY_PARAM);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath(), is("/path/slash%2Fpath?query=slash%2Fquery"));
    }

    @Path("/")
    public interface FakeoInterface {
        @GET
        @Path("path/{path}")
        String encoded(@PathParam("path") String path, @QueryParam("query") String query);
    }

    public static class SlashEncodingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(fakeoResource);
        }
    }

}
