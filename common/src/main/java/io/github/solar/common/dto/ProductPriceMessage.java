package io.github.solar.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Общий контракт сообщения о цене товара для обмена между микросервисами через Kafka.
 * Используется парсером (producer) и аналитикой (consumer) в топике {@code raw-prices}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceMessage {

    private String externalId;
    private String title;
    private BigDecimal price;
    private String url;
}
