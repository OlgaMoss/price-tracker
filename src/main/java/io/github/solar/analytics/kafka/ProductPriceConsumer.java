package io.github.solar.analytics.kafka;

import io.github.solar.analytics.dto.ProductPriceMessage;
import io.github.solar.analytics.service.PriceAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductPriceConsumer {

    private final PriceAnalysisService priceAnalysisService;

    @KafkaListener(topics = "raw-prices", groupId = "analytics-group")
    public void listenProductPrices(ProductPriceMessage message) {
        try {
            log.info("Kafka Consumer успешно принял сообщение для ID: {}", message.getExternalId());
            priceAnalysisService.analyzePrice(message);
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения из Kafka: ", e);
        }
    }
}

