/**
 * Copyright 2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package reactivefeign.jetty;

import com.fasterxml.jackson.core.async_.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.http3.client.HTTP3Client;
import org.eclipse.jetty.http3.client.transport.HttpClientTransportOverHTTP3;
import org.eclipse.jetty.quic.client.ClientQuicConfiguration;
import reactivefeign.ReactiveFeign;
import reactivefeign.ReactiveFeignBuilder;
import reactivefeign.ReactiveOptions;
import reactivefeign.client.ReactiveHttpClientFactory;
import reactivefeign.jetty.client.JettyReactiveHttpClient;

import javax.net.ssl.SSLServerSocketFactory;

/**
 * Reactive Jetty client based implementation of reactive Feign
 *
 * @author Sergii Karpenko
 */
public final class JettyReactiveFeign {

    private JettyReactiveFeign(){}
//FIXME

//    public static <T> Builder<T> builder() {
//
//        return builder(useHttp3 -> {
//            try {
//                if(useHttp3){
//
////previous
//////                    HTTP2Client h2Client = new HTTP2Client();
//////                    h2Client.setSelectors(1);
//////                    HttpClientTransportOverHTTP2 transport = new HttpClientTransportOverHTTP2(h2Client);
//
//                    HTTP3Client http3Client = new HTTP3Client(new ClientQuicConfiguration(SSLServerSocketFactory.getDefault()));
//                    HttpClientTransportOverHTTP3 transport = new HttpClientTransportOverHTTP3(http3Client);
//
//                    HttpClient httpClient = new HttpClient(transport);
//                    httpClient.start();
//                    return httpClient;
//                } else {
//                    HttpClient httpClient = new HttpClient();
//                    httpClient.start();
//                    return httpClient;
//                }
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }

    public static <T> Builder<T> builder(HttpClient httpClient) {
        return builder(useHttp2 -> httpClient);
    }

    public static <T> Builder<T> builder(JettyHttpClientFactory httpClientFactory) {
        return new Builder<>(httpClientFactory, new JsonFactory());
    }

    public static class Builder<T> extends ReactiveFeign.Builder<T> {

        protected JettyHttpClientFactory httpClientFactory;
        protected JsonFactory jsonFactory;
        private ObjectMapper objectMapper;
        protected JettyReactiveOptions options;

        protected Builder(JettyHttpClientFactory httpClientFactory, JsonFactory jsonFactory) {
            this.jsonFactory = jsonFactory;
            this.objectMapper = new ObjectMapper().findAndRegisterModules();
            setHttpClient(httpClientFactory);
        }

        @Override
        public ReactiveFeignBuilder<T> objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        @Override
        public Builder<T> options(ReactiveOptions options) {
            this.options = (JettyReactiveOptions)options;
            return this;
        }

        protected void setHttpClient(JettyHttpClientFactory httpClientFactory){
            this.httpClientFactory = httpClientFactory;
        }

        @Override
        protected ReactiveHttpClientFactory clientFactory() {
            boolean useHttp2 = ReactiveOptions.useHttp2(options);
            HttpClient httpClient = httpClientFactory.build(useHttp2);

//            if(useHttp2 && !(httpClient.getTransport() instanceof HttpClientTransportOverHTTP2)){
            if(useHttp2 && !(httpClient.getTransport() instanceof HttpClientTransportOverHTTP3)){
//                throw new IllegalArgumentException("HttpClient should use HttpClientTransportOverHTTP2");
                throw new IllegalArgumentException("HttpClient should use HttpClientTransportOverHTTP3");
            }

            if (this.options != null && this.options.getConnectTimeoutMillis() != null) {
                httpClient.setConnectTimeout(options.getConnectTimeoutMillis());
            }

            if(options != null && this.options.isFollowRedirects() != null){
                httpClient.setFollowRedirects(this.options.isFollowRedirects());
            }

            if(options != null && this.options.getProxySettings() != null){
                ReactiveOptions.ProxySettings proxySettings = this.options.getProxySettings();
                httpClient.getProxyConfiguration().getProxies()
                        .add(new HttpProxy(proxySettings.getHost(), proxySettings.getPort()));
            }

            return methodMetadata -> {
                JettyReactiveHttpClient jettyClient = JettyReactiveHttpClient.jettyClient(methodMetadata, httpClient, jsonFactory, objectMapper);
                if (options != null && options.getRequestTimeoutMillis() != null) {
                    jettyClient = jettyClient.setRequestTimeout(options.getRequestTimeoutMillis());
                }
                if (options != null && options.isTryUseCompression() != null) {
                    jettyClient = jettyClient.setTryUseCompression(options.isTryUseCompression());
                }
                return jettyClient;
            };
        }
    }
}


