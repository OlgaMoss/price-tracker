package io.github.solar.analytics.mapper;

import io.github.solar.analytics.entity.Product;
import io.github.solar.common.dto.ProductPriceMessage;

import java.time.LocalDateTime;

/**
 * Маппер для преобразования Kafka-сообщений в JPA-сущности товара.
 */
public final class ProductMapper {

	private ProductMapper() {
	}

	/**
	 * Создаёт новую сущность товара из входящего сообщения о цене.
	 *
	 * @param message сообщение из топика {@code raw-prices}
	 * @return новая сущность {@link Product} без идентификатора БД
	 */
	public static Product toNewProduct(ProductPriceMessage message) {
		return Product.builder()
				.externalId(message.getExternalId())
				.title(message.getTitle())
				.currentPrice(message.getPrice())
				.url(message.getUrl())
				.updatedAt(LocalDateTime.now())
				.build();
	}
}
