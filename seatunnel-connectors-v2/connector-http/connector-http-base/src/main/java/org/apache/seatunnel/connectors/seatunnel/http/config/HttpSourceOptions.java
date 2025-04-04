/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.http.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;

import java.util.Map;

public class HttpSourceOptions extends HttpCommonOptions {

    public static final boolean DEFAULT_ENABLE_MULTI_LINES = false;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 6000 * 2;
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 6000 * 10;

    public static final Option<Boolean> KEEP_PARAMS_AS_FORM =
            Options.key("keep_params_as_form")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Keep param as form");

    public static final Option<Boolean> KEEP_PAGE_PARAM_AS_HTTP_PARAM =
            Options.key("keep_page_param_as_http_param")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("keep page param as http param");

    public static final Option<Long> TOTAL_PAGE_SIZE =
            Options.key("total_page_size")
                    .longType()
                    .defaultValue(0L)
                    .withDescription("total page size");
    public static final Option<Integer> BATCH_SIZE =
            Options.key("batch_size")
                    .intType()
                    .defaultValue(100)
                    .withDescription(
                            "the batch size returned per request is used to determine whether to continue when the total number of pages is unknown");
    public static final Option<Long> START_PAGE_NUMBER =
            Options.key("start_page_number")
                    .longType()
                    .defaultValue(1L)
                    .withDescription("which page to start synchronizing from");
    public static final Option<String> PAGE_FIELD =
            Options.key("page_field")
                    .stringType()
                    .defaultValue("page")
                    .withDescription(
                            "this parameter is used to specify the page field name in the request parameter");

    public static final Option<Map<String, String>> PAGEING =
            Options.key("pageing").mapType().noDefaultValue().withDescription("pageing");

    public static final Option<HttpRequestMethod> METHOD =
            Options.key("method")
                    .enumType(HttpRequestMethod.class)
                    .defaultValue(HttpRequestMethod.GET)
                    .withDescription("Http request method");

    public static final Option<String> BODY =
            Options.key("body").stringType().noDefaultValue().withDescription("Http request body");

    public static final Option<HttpConfig.ResponseFormat> FORMAT =
            Options.key("format")
                    .enumType(HttpConfig.ResponseFormat.class)
                    .defaultValue(HttpConfig.ResponseFormat.TEXT)
                    .withDescription("Http response format");
    public static final Option<Integer> POLL_INTERVAL_MILLS =
            Options.key("poll_interval_millis")
                    .intType()
                    .noDefaultValue()
                    .withDescription("Request http api interval(millis) in stream mode");

    public static final Option<JsonField> JSON_FIELD =
            Options.key("json_field")
                    .objectType(JsonField.class)
                    .noDefaultValue()
                    .withDescription(
                            "SeaTunnel json field.When partial json data is required, this parameter can be configured to obtain data");
    public static final Option<String> CONTENT_FIELD =
            Options.key("content_field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "SeaTunnel content field.This parameter can get some json data, and there is no need to configure each field separately.");

    public static final Option<Boolean> ENABLE_MULTI_LINES =
            Options.key("enable_multi_lines")
                    .booleanType()
                    .defaultValue(DEFAULT_ENABLE_MULTI_LINES)
                    .withDescription(
                            "SeaTunnel enableMultiLines.This parameter can support http splitting response text by line.");

    public static final Option<Integer> CONNECT_TIMEOUT_MS =
            Options.key("connect_timeout_ms")
                    .intType()
                    .defaultValue(DEFAULT_CONNECT_TIMEOUT_MS)
                    .withDescription("Connection timeout setting, default 12s.");

    public static final Option<Integer> SOCKET_TIMEOUT_MS =
            Options.key("socket_timeout_ms")
                    .intType()
                    .defaultValue(DEFAULT_SOCKET_TIMEOUT_MS)
                    .withDescription("Socket timeout setting, default 60s.");
}
