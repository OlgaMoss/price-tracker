package io.github.solar.analytics.service;

import io.github.solar.analytics.entity.PriceHistory;
import io.github.solar.analytics.entity.Product;
import io.github.solar.analytics.dto.ProductPriceMessage;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceAnalysisService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    @Transactional
    public void analyzePrice(ProductPriceMessage message) {
        log.info("Получены данные для анализа: {} - Цена: {}", message.getTitle(), message.getPrice());

        Optional<Product> existingProductOpt = productRepository.findByExternalId(message.getExternalId());

        if (existingProductOpt.isEmpty()) {
            log.info("Новый товар обнаружен. Сохраняем в базу: {}", message.getTitle());

            Product newProduct = Product.builder()
                    .externalId(message.getExternalId())
                    .title(message.getTitle())
                    .currentPrice(message.getPrice())
                    .url(message.getUrl())
                    .updatedAt(LocalDateTime.now())
                    .build();

            productRepository.save(newProduct);
            savePriceHistory(newProduct, message.getPrice());
        } else {
            Product product = existingProductOpt.get();

            if (message.getPrice().compareTo(product.getCurrentPrice()) < 0) {
                log.info("ЦЕНА УПАЛА! Старая цена: {}, Новая цена: {}", product.getCurrentPrice(), message.getPrice());

                // TODO: Здесь в будущем будет отправка события в топик price-drops для уведомлений
            }

            if (message.getPrice().compareTo(product.getCurrentPrice()) != 0) {
                product.setCurrentPrice(message.getPrice());
                product.setUpdatedAt(LocalDateTime.now());
                productRepository.save(product);

                savePriceHistory(product, message.getPrice());
            } else {
                log.info("Цена на товар '{}' не изменилась.", product.getTitle());
            }
        }
    }

    private void savePriceHistory(Product product, java.math.BigDecimal price) {
        PriceHistory history = PriceHistory.builder()
                .product(product)
                .price(price)
                .checkedAt(LocalDateTime.now())
                .build();
        priceHistoryRepository.save(history);
    }
}

