/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.wnaregistry.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Getter;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

public class WnaRegistryClient {
  private final String host;
  private final String user;
  private final String password;
  private final OkHttpClient okHttpClient;

  @Getter private BuildItemService buildItemService;
  @Getter private ConcourseApiService concourseApiService;

  private JacksonConverter jacksonConverter;

  private final RequestInterceptor basicAuthInterceptor =
      new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade request) {
          request.addHeader(
              "Authorization",
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString(
                          (user + ":" + password).getBytes(StandardCharsets.ISO_8859_1)));
        }
      };

  public WnaRegistryClient(String host, String user, String password) {
    this.host = host;
    this.user = user;
    this.password = password;

    ObjectMapper mapper =
        new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

    this.okHttpClient = OkHttpClientBuilder.client();
    this.jacksonConverter = new JacksonConverter(mapper);

    this.buildItemService = createService(BuildItemService.class);
    this.concourseApiService = createService(ConcourseApiService.class);
  }

  private <S> S createService(Class<S> serviceClass) {
    return new RestAdapter.Builder()
        .setEndpoint(host)
        .setClient(new OkClient(okHttpClient))
        .setConverter(jacksonConverter)
        .setRequestInterceptor(basicAuthInterceptor)
        .setLog(new Slf4jRetrofitLogger(serviceClass))
        .build()
        .create(serviceClass);
  }
}
