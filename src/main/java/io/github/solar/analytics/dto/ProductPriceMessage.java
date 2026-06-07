package io.github.solar.analytics.dto;

import lombok.*;

import java.math.BigDecimal;

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
