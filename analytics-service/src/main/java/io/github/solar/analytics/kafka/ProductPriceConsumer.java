package io.github.solar.analytics.kafka;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.analytics.exception.KafkaMessageProcessingException;
import io.github.solar.analytics.service.PriceAnalysisService;
import io.github.solar.common.dto.ProductPriceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer сырых цен из топика {@code raw-prices}.
 * Делегирует бизнес-логику анализа сервису {@link PriceAnalysisService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductPriceConsumer {

	private final PriceAnalysisService priceAnalysisService;

	/**
	 * Обрабатывает входящее сообщение о цене товара.
	 *
	 * @param message десериализованное сообщение из Kafka
	 */
	@KafkaListener(topics = "raw-prices", groupId = "analytics-group")
	public void listenProductPrices(ProductPriceMessage message) {
		String externalId = message == null ? null : message.getExternalId();
		try {
			log.info("Kafka consumer принял сообщение: externalId={}", externalId);
			priceAnalysisService.analyzePrice(message);
		} catch (InvalidProductPriceMessageException e) {
			log.error("Некорректное сообщение из Kafka: externalId={}, field={}", externalId, e.getField(), e);
			throw e;
		} catch (Exception e) {
			log.error("Ошибка при обработке сообщения из Kafka: externalId={}", externalId, e);
			throw new KafkaMessageProcessingException(
					"Не удалось обработать сообщение для externalId=" + externalId, e);
		}
	}
}
