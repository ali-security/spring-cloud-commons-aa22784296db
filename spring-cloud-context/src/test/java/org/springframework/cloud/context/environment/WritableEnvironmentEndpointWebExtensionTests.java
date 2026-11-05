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

import org.junit.Test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.context.environment.WritableEnvironmentEndpointWebExtension.VALID_KEYS_REGEX_PROPERTY;

public class WritableEnvironmentEndpointWebExtensionTests {

	private final ApplicationEventPublisher publisher = mock(
			ApplicationEventPublisher.class);

	private MockEnvironment environment = new MockEnvironment();

	private WritableEnvironmentEndpointWebExtension extension;

	private void createExtension() {
		EnvironmentManager environmentManager = new EnvironmentManager(this.environment);
		environmentManager.setApplicationEventPublisher(this.publisher);
		this.extension = new WritableEnvironmentEndpointWebExtension(
				new WritableEnvironmentEndpoint(this.environment), environmentManager);
	}

	private void assertAllowed(String key) {
		then(this.extension.write(key, "Foo"))
				.isEqualTo(Collections.singletonMap(key, "Foo"));
		then(this.environment.getProperty(key)).isEqualTo("Foo");
	}

	private void assertRejected(String key) {
		thenThrownBy(() -> this.extension.write(key, "Foo"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key " + key);
		then(this.environment.getProperty(key)).isNull();
	}

	@Test
	public void writeAllowedWhenNoValidKeysRegexConfigured() {
		createExtension();

		assertAllowed("message");
		assertAllowed("logging.level.org.springframework");
		assertAllowed("myapp.datasource.url");
		assertAllowed("eureka.instance.metadata-map.zone");
		verify(this.publisher, times(4)).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeAllowedWhenValidKeysRegexIsBlank() {
		this.environment.setProperty(VALID_KEYS_REGEX_PROPERTY, " ");
		createExtension();

		assertAllowed("message");
		assertRejected("spring.datasource.url");
		verify(this.publisher, times(1)).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeRejectedForDeniedKeysWhenNoValidKeysRegexConfigured() {
		createExtension();

		assertRejected("spring.cloud.bootstrap.location");
		assertRejected("eureka.client.serviceUrl.defaultZone");
		assertRejected("logging.config");
		assertRejected("spring.main.sources");
		assertRejected("spring.datasource.url");
		assertRejected("spring.datasource.hikari.connection-test-query");
		assertRejected("spring.datasource.data");
		assertRejected("spring.cloud.config.uri");
		assertRejected("spring.config.location");
		assertRejected("spring.application.json");
		assertRejected("spring.h2.console.enabled");
		assertRejected("spring.liquibase.change-log");
		assertRejected("spring.batch.schema");
		assertRejected("spring.cloud.function.routing-expression");
		assertRejected("spring.resources.static-locations");
		assertRejected("management.endpoints.web.exposure.include");
		assertRejected(VALID_KEYS_REGEX_PROPERTY);
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeRejectedForRelaxedSpellingsOfDeniedKeys() {
		createExtension();

		assertRejected("SPRING.CLOUD.BOOTSTRAP.LOCATION");
		assertRejected("spring.cloud.boot-strap.location");
		assertRejected("spring.data-source.url");
		assertRejected("spring_datasource_url");
		assertRejected("logging.con_fig");
		assertRejected("spring.data_source.url");
		assertRejected("spring.cloud.boot_strap.location");
		assertRejected("eu_reka.client.serviceUrl.defaultZone");
		assertRejected("spring.ma_in.sources");
		assertRejected("eureka.client.serviceUrl[defaultZone]");
		assertRejected(" logging.config ");
		assertRejected(".logging..config");
		assertRejected("Logging.Config");
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeRejectedWhenKeyDoesNotMatchValidKeysRegex() {
		this.environment.setProperty(VALID_KEYS_REGEX_PROPERTY, "message");
		createExtension();

		assertRejected("xmessage");
		// the whole key must match, not only a part of it
		assertRejected("messagex");
		// a key outside the deny list is still rejected when the regex is set
		assertRejected("myapp.flag");
		// the allow list itself cannot be widened through the endpoint
		thenThrownBy(() -> this.extension.write(VALID_KEYS_REGEX_PROPERTY, ".*"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key " + VALID_KEYS_REGEX_PROPERTY);
		then(this.environment.getProperty(VALID_KEYS_REGEX_PROPERTY))
				.isEqualTo("message");
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeAllowedWhenKeyMatchesValidKeysRegex() {
		this.environment.setProperty(VALID_KEYS_REGEX_PROPERTY, "message");
		createExtension();

		assertAllowed("message");
		verify(this.publisher, times(1)).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void validKeysRegexReplacesDeniedKeys() {
		this.environment.setProperty(VALID_KEYS_REGEX_PROPERTY,
				"spring\\.datasource\\.url");
		createExtension();

		assertAllowed("spring.datasource.url");
	}

	@Test
	public void normalizeMatchesRelaxedBindingForms() {
		then(normalize("eureka.client.serviceUrl[defaultZone]"))
				.isEqualTo("eureka.client.serviceurl.defaultzone");
		then(normalize("logging.con_fig")).isEqualTo("logging.config");
		then(normalize("spring.data-source.url")).isEqualTo("spring.datasource.url");
		then(normalize("..a..b..")).isEqualTo("a.b");
	}

	private String normalize(String name) {
		return WritableEnvironmentEndpointWebExtension.normalize(name);
	}

}
