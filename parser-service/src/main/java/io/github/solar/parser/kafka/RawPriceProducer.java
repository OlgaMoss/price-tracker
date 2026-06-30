package io.github.solar.parser.kafka;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.exception.PricePublishingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer для публикации сырых цен в топик {@code raw-prices}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RawPriceProducer {

	private final KafkaTemplate<String, ProductPriceMessage> kafkaTemplate;
	private final SteamParserProperties properties;

	/**
	 * Публикует сообщение о цене товара в Kafka.
	 *
	 * @param message сообщение для топика {@code raw-prices}
	 */
	public void publish(ProductPriceMessage message) {
		try {
			kafkaTemplate.send(properties.getKafkaTopic(), message.getExternalId(), message).get();
			log.info("Цена опубликована в Kafka: topic={}, externalId={}, price={}",
					properties.getKafkaTopic(), message.getExternalId(), message.getPrice());
		} catch (Exception e) {
			throw new PricePublishingException(
					"Не удалось опубликовать цену в Kafka: externalId=" + message.getExternalId(), e);
		}
	}
}
