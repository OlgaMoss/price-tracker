package io.github.solar.parser.runner;

import io.github.solar.parser.service.SteamPriceParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Однократный запуск парсера при старте контейнера (режим K8s CronJob).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "steam.parser.run-once", havingValue = "true")
public class RunOnceParserRunner implements ApplicationRunner {

	private final SteamPriceParsingService parsingService;
	private final ConfigurableApplicationContext applicationContext;

	@Override
	public void run(ApplicationArguments args) {
		log.info("Запуск парсера в режиме однократного выполнения (K8s CronJob)");
		parsingService.parseAndPublishAll();
		int exitCode = SpringApplication.exit(applicationContext, () -> 0);
		System.exit(exitCode);
	}
}
