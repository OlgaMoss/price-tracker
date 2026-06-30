package io.github.solar.parser.service;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.client.SteamApiClient;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.kafka.RawPriceProducer;
import io.github.solar.parser.mapper.SteamPriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Оркестратор парсинга: запрашивает цены из Steam API и публикует их в Kafka.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SteamPriceParsingService {

	private final SteamApiClient steamApiClient;
	private final RawPriceProducer rawPriceProducer;
	private final SteamParserProperties properties;

	/**
	 * Обходит все настроенные приложения Steam, извлекает цены и публикует в Kafka.
	 */
	public void parseAndPublishAll() {
		List<Integer> appIds = properties.getAppIds();
		log.info("Старт парсинга Steam: appCount={}, countryCode={}", appIds.size(), properties.getCountryCode());

		int publishedCount = 0;
		for (int i = 0; i < appIds.size(); i++) {
			int appId = appIds.get(i);
			publishedCount += parseAndPublish(appId);

			if (i < appIds.size() - 1) {
				sleepBetweenRequests();
			}
		}

		log.info("Парсинг завершён: опубликовано сообщений={}", publishedCount);
	}

	private int parseAndPublish(int appId) {
		return steamApiClient.fetchAppDetails(appId)
				.flatMap(SteamPriceMapper::toProductPriceMessage)
				.map(message -> {
					rawPriceProducer.publish(message);
					return 1;
				})
				.orElseGet(() -> {
					log.warn("Цена не получена, пропускаем appId={}", appId);
					return 0;
				});
	}

	private void sleepBetweenRequests() {
		try {
			Thread.sleep(properties.getRequestDelayMs());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Парсинг прерван во время задержки между запросами", e);
			throw new IllegalStateException("Парсинг прерван", e);
		}
	}
}
