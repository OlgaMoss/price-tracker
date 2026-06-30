package io.github.solar.parser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация инфраструктурных бинов парсера.
 */
@Configuration
public class ParserBeanConfig {

	@Bean
	public RestClient steamRestClient() {
		return RestClient.builder()
				.defaultHeader("User-Agent", "SteamPriceMonitor/1.0")
				.build();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
