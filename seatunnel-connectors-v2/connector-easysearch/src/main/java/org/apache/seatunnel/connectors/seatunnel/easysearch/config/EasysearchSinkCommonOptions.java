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

package org.apache.seatunnel.connectors.seatunnel.easysearch.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;

import java.util.List;

public class EasysearchSinkCommonOptions {

    public static final Option<List<String>> HOSTS =
            Options.key("hosts")
                    .listType()
                    .noDefaultValue()
                    .withDescription(
                            "Easysearch cluster http address, the format is host:port, allowing multiple hosts to be specified. Such as [\"host1:9200\", \"host2:9200\"]");

    public static final Option<String> INDEX =
            Options.key("index")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Easysearch index name, support * fuzzy matching");

    public static final Option<String> USERNAME =
            Options.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("security username");

    public static final Option<String> PASSWORD =
            Options.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("security password");

    public static final Option<Boolean> TLS_VERIFY_CERTIFICATE =
            Options.key("tls_verify_certificate")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Enable certificates validation for HTTPS endpoints");

    public static final Option<Boolean> TLS_VERIFY_HOSTNAME =
            Options.key("tls_verify_hostname")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Enable hostname validation for HTTPS endpoints");

    public static final Option<String> TLS_KEY_STORE_PATH =
            Options.key("tls_keystore_path")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The path to the PEM or JKS key store. This file must be readable by the operating system user running SeaTunnel.");

    public static final Option<String> TLS_KEY_STORE_PASSWORD =
            Options.key("tls_keystore_password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The key password for the key store specified");

    public static final Option<String> TLS_TRUST_STORE_PATH =
            Options.key("tls_truststore_path")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The path to PEM or JKS trust store. This file must be readable by the operating system user running SeaTunnel.");

    public static final Option<String> TLS_TRUST_STORE_PASSWORD =
            Options.key("tls_truststore_password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The key password for the trust store specified");
}
