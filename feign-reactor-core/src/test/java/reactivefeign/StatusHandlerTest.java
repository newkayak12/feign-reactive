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
package reactivefeign;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import reactivefeign.testcase.IcecreamServiceApi;
import reactor.test.StepVerifier;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.function.Predicate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static reactivefeign.client.statushandler.CompositeStatusHandler.compose;
import static reactivefeign.client.statushandler.ReactiveStatusHandlers.throwOnStatus;
import static reactivefeign.utils.FeignUtils.httpMethod;
import static reactivefeign.utils.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static reactivefeign.utils.HttpStatus.SC_UNAUTHORIZED;

/**
 * @author Sergii Karpenko
 */
public abstract class StatusHandlerTest extends BaseReactorTest {

  @Rule
  public WireMockClassRule wireMockRule = new WireMockClassRule(
      wireMockConfig().dynamicPort());

  abstract protected ReactiveFeignBuilder<IcecreamServiceApi> builder();

  protected WireMockConfiguration wireMockConfig(){
    return WireMockConfiguration.wireMockConfig();
  }

  @Before
  public void resetServers() {
    wireMockRule.resetAll();
  }

  @Test
  public void shouldThrowCustomExceptionOnMono() {

    IcecreamServiceApi client = builder()
            .statusHandler(throwOnStatus(
                    status -> status == SC_SERVICE_UNAVAILABLE,
                    (methodTag, response) -> new UnsupportedOperationException("Custom error")))
            .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());


    wireMockRule.stubFor(get(urlEqualTo("/icecream/orders/1"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(SC_SERVICE_UNAVAILABLE)));

    StepVerifier.create(client.findFirstOrder().subscribeOn(testScheduler()))
        .expectErrorMatches(customException())
        .verify();

    //should be processed by default status handler
    wireMockRule.stubFor(get(urlEqualTo("/icecream/orders/1"))
            .withHeader("Accept", equalTo("application/json"))
            .willReturn(aResponse().withStatus(403)));

    StepVerifier.create(client.findFirstOrder())
            .expectErrorMatches(feignDefaultException())
            .verify();
  }

  @Test
  public void shouldThrowCustomExceptionOnFlux() {

    IcecreamServiceApi client = builder()
            .statusHandler(throwOnStatus(
                    status -> status == SC_SERVICE_UNAVAILABLE,
                    (methodTag, response) -> new UnsupportedOperationException("Custom error")))
            .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());


    wireMockRule.stubFor(get(urlEqualTo("/icecream/mixins"))
            .willReturn(aResponse().withStatus(SC_SERVICE_UNAVAILABLE)));

    StepVerifier.create(client.getAvailableMixins().subscribeOn(testScheduler()))
            .expectErrorMatches(customException())
            .verify();

    //should be processed by default status handler
    wireMockRule.stubFor(get(urlEqualTo("/icecream/mixins"))
            .willReturn(aResponse().withStatus(403)));

    StepVerifier.create(client.findFirstOrder())
            .expectErrorMatches(feignDefaultException())
            .verify();
  }

  protected Predicate<Throwable> customException(){
    return throwable -> throwable instanceof UnsupportedOperationException;
  }

  protected Predicate<Throwable> feignDefaultException(){
    return throwable -> throwable instanceof FeignException;
  }

  @Test
  public void shouldThrowOnStatusCode() {
    wireMockRule.stubFor(get(urlEqualTo("/icecream/orders/1"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(SC_SERVICE_UNAVAILABLE)));

    wireMockRule.stubFor(get(urlEqualTo("/icecream/orders/2"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(SC_UNAUTHORIZED)));


    IcecreamServiceApi client = builder()
        .statusHandler(compose(
            throwOnStatus(
                status -> status == SC_SERVICE_UNAVAILABLE,
                (methodTag, response) -> {
                  Request.HttpMethod httpMethod = httpMethod(response.request().method());
                  Request request = Request.create(httpMethod, response.request().uri().toString(),
                          Collections.emptyMap(), new byte[0], Charset.defaultCharset());
                  return new RetryableException(
                          response.status(),
                          "Should retry on next node",
                          httpMethod, null, 0L, request);
                }),
            throwOnStatus(
                status -> status == SC_UNAUTHORIZED,
                (methodTag, response) -> new RuntimeException("Should login", null))))
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    StepVerifier.create(client.findFirstOrder().subscribeOn(testScheduler()))
        .expectErrorMatches(customException1())
        .verify();

    StepVerifier.create(client.findOrder(2))
        .expectErrorMatches(customException2())
        .verify();
  }

  protected Predicate<Throwable> customException1(){
    return throwable -> throwable instanceof RetryableException;
  }

  protected Predicate<Throwable> customException2(){
    return throwable -> throwable instanceof RuntimeException;
  }
}
