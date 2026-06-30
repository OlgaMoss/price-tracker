package io.github.solar.parser.scheduler;

import io.github.solar.parser.service.SteamPriceParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик периодического парсинга цен Steam (режим долгоживущего сервиса).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "steam.parser.run-once", havingValue = "false", matchIfMissing = true)
public class SteamPriceParserScheduler {

	private final SteamPriceParsingService parsingService;

	/**
	 * Запускает парсинг по cron-расписанию из конфигурации.
	 */
	@Scheduled(cron = "${steam.parser.schedule-cron}")
	public void scheduledParse() {
		log.info("Запуск парсинга по расписанию");
		parsingService.parseAndPublishAll();
	}
}
