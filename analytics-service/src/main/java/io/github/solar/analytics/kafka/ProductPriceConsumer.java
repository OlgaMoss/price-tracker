package io.github.solar.analytics.kafka;

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
		try {
			log.info("Kafka consumer принял сообщение: externalId={}", message.getExternalId());
			priceAnalysisService.analyzePrice(message);
		} catch (Exception e) {
			log.error("Ошибка при обработке сообщения из Kafka: externalId={}", message.getExternalId(), e);
			throw new KafkaMessageProcessingException(
					"Не удалось обработать сообщение для externalId=" + message.getExternalId(), e);
		}
	}
}
