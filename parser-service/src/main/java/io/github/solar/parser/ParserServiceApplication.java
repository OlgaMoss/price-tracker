package io.github.solar.parser;

import io.github.solar.parser.config.SteamParserProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа микросервиса парсера цен Steam.
 * По расписанию (или однократно в K8s CronJob) запрашивает Steam Store API
 * и публикует сырые цены в Kafka-топик {@code raw-prices}.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SteamParserProperties.class)
public class ParserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParserServiceApplication.class, args);
	}
}
