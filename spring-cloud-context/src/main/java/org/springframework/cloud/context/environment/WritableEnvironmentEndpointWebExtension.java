/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.context.environment;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.env.EnvironmentEndpointWebExtension;
import org.springframework.util.StringUtils;

/**
 * MVC endpoint for the {@link EnvironmentManager}, providing a POST to /env as a simple
 * way to change the Environment.
 *
 * @author Dave Syer
 *
 */
@EndpointWebExtension(endpoint = WritableEnvironmentEndpoint.class)
public class WritableEnvironmentEndpointWebExtension
		extends EnvironmentEndpointWebExtension {

	/**
	 * The property for writable valid keys regular expression.
	 */
	public static final String VALID_KEYS_REGEX_PROPERTY = "management.endpoint.env.post.valid-keys-regex";

	/**
	 * Keys that POST /env rejects when {@link #VALID_KEYS_REGEX_PROPERTY} is not set.
	 */
	private static final Set<String> DENIED_KEYS = new LinkedHashSet<>();

	static {
		// actuator configuration, including the allow list itself
		DENIED_KEYS.add("management");
		DENIED_KEYS.add("endpoints");
		// remote or alternate configuration sources
		DENIED_KEYS.add("spring.config");
		DENIED_KEYS.add("spring.application.json");
		DENIED_KEYS.add("spring.profiles");
		DENIED_KEYS.add("spring.cloud.bootstrap");
		DENIED_KEYS.add("spring.cloud.config");
		DENIED_KEYS.add("spring.cloud.consul");
		DENIED_KEYS.add("spring.cloud.zookeeper");
		DENIED_KEYS.add("spring.cloud.vault");
		DENIED_KEYS.add("spring.cloud.kubernetes");
		// sources and bean definitions loaded on restart
		DENIED_KEYS.add("spring.main");
		// logging configuration files (logback JNDI, Groovy configuration)
		DENIED_KEYS.add("logging.config");
		DENIED_KEYS.add("logging.file");
		DENIED_KEYS.add("logging.path");
		// Eureka service URL (XStream deserialization)
		DENIED_KEYS.add("eureka.client");
		// JDBC URLs, init SQL and schema or migration scripts
		DENIED_KEYS.add("spring.datasource");
		DENIED_KEYS.add("spring.jpa");
		DENIED_KEYS.add("spring.h2");
		DENIED_KEYS.add("spring.liquibase");
		DENIED_KEYS.add("liquibase");
		DENIED_KEYS.add("spring.flyway");
		DENIED_KEYS.add("flyway");
		DENIED_KEYS.add("spring.batch");
		DENIED_KEYS.add("spring.quartz.jdbc");
		DENIED_KEYS.add("spring.session.jdbc");
		DENIED_KEYS.add("spring.integration.jdbc");
		// SpEL in gateway routes and function routing expressions
		DENIED_KEYS.add("spring.cloud.gateway");
		DENIED_KEYS.add("spring.cloud.function");
		// template and static resource locations
		DENIED_KEYS.add("spring.freemarker");
		DENIED_KEYS.add("spring.thymeleaf");
		DENIED_KEYS.add("spring.groovy.template");
		DENIED_KEYS.add("spring.resources.staticlocations");
		// security configuration
		DENIED_KEYS.add("spring.security");
	}

	private final Pattern validKeysPattern;

	private EnvironmentManager environment;

	public WritableEnvironmentEndpointWebExtension(WritableEnvironmentEndpoint endpoint,
			EnvironmentManager environment) {
		super(endpoint);
		this.environment = environment;
		String validKeysRegex = this.environment.getEnvironment()
				.getProperty(VALID_KEYS_REGEX_PROPERTY);
		if (StringUtils.hasText(validKeysRegex)) {
			validKeysPattern = Pattern.compile(validKeysRegex);
		}
		else {
			validKeysPattern = null;
		}
	}

	@WriteOperation
	public Object write(String name, String value) {
		if (isAllowed(name)) {
			this.environment.setProperty(name, value);
			return Collections.singletonMap(name, value);
		}
		throw new IllegalArgumentException("Invalid key " + name);
	}

	@DeleteOperation
	public Map<String, Object> reset() {
		return this.environment.reset();
	}

	public void setEnvironmentManager(EnvironmentManager environment) {
		this.environment = environment;
	}

	private boolean isAllowed(String name) {
		if (validKeysPattern != null) {
			return validKeysPattern.matcher(name).matches();
		}
		// relaxed binding ignores '_' inside an element, but the system environment
		// mapping reads it as a separator: reject the key if either form is denied
		String key = normalize(name);
		String separated = normalize(name.replace('_', '.'));
		return !isDenied(key) && !isDenied(separated);
	}

	private boolean isDenied(String key) {
		for (String denied : DENIED_KEYS) {
			if (key.equals(denied) || key.startsWith(denied + ".")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reduce a key to the form that relaxed binding compares.
	 * @param name the property key
	 * @return the key in lower case, with only letters, digits and dots
	 */
	static String normalize(String name) {
		StringBuilder key = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = Character.toLowerCase(name.charAt(i));
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				key.append(c);
			}
			else if (c == '.' || c == '[' || c == ']') {
				if (key.length() > 0 && key.charAt(key.length() - 1) != '.') {
					key.append('.');
				}
			}
		}
		if (key.length() > 0 && key.charAt(key.length() - 1) == '.') {
			key.setLength(key.length() - 1);
		}
		return key.toString();
	}

}
