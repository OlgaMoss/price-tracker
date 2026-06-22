package io.github.solar.analytics.service;

import io.github.solar.analytics.entity.PriceHistory;
import io.github.solar.analytics.entity.Product;
import io.github.solar.analytics.mapper.ProductMapper;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import io.github.solar.common.dto.ProductPriceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис аналитики цен. Отвечает за сопоставление входящих сырых данных
 * из Kafka с историческими данными в БД и расчёт дельты скидки.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceAnalysisService {

	private final ProductRepository productRepository;
	private final PriceHistoryRepository priceHistoryRepository;

	/**
	 * Анализирует входящее сообщение о цене: создаёт товар или обновляет существующий,
	 * фиксирует историю при изменении цены.
	 *
	 * @param message сырое сообщение из топика {@code raw-prices}
	 */
	@Transactional
	public void analyzePrice(ProductPriceMessage message) {
		log.info("Получены данные для анализа: externalId={}, title={}, price={}",
				message.getExternalId(), message.getTitle(), message.getPrice());

		Optional<Product> existingProductOpt = productRepository.findByExternalId(message.getExternalId());

		if (existingProductOpt.isEmpty()) {
			registerNewProduct(message);
			return;
		}

		updateExistingProduct(existingProductOpt.get(), message);
	}

	private void registerNewProduct(ProductPriceMessage message) {
		log.info("Новый товар обнаружен. Сохраняем в базу: externalId={}, title={}",
				message.getExternalId(), message.getTitle());

		Product newProduct = productRepository.save(ProductMapper.toNewProduct(message));
		savePriceHistory(newProduct, message.getPrice());
	}

	private void updateExistingProduct(Product product, ProductPriceMessage message) {
		if (message.getPrice().compareTo(product.getCurrentPrice()) < 0) {
			log.info("Цена упала: externalId={}, oldPrice={}, newPrice={}",
					product.getExternalId(), product.getCurrentPrice(), message.getPrice());
			// TODO: публикация события в топик price-drops для notification-service
		}

		if (message.getPrice().compareTo(product.getCurrentPrice()) != 0) {
			product.setCurrentPrice(message.getPrice());
			product.setUpdatedAt(LocalDateTime.now());
			productRepository.save(product);
			savePriceHistory(product, message.getPrice());
			return;
		}

		log.info("Цена не изменилась: externalId={}, title={}", product.getExternalId(), product.getTitle());
	}

	private void savePriceHistory(Product product, BigDecimal price) {
		PriceHistory history = PriceHistory.builder()
				.product(product)
				.price(price)
				.checkedAt(LocalDateTime.now())
				.build();
		priceHistoryRepository.save(history);
	}
}
