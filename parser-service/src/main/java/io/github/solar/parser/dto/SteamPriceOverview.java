package io.github.solar.parser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO блока цены из Steam Store API. Суммы указаны в минимальных единицах валюты (копейки).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamPriceOverview {

	private String currency;
	private int initial;

	@JsonProperty("final")
	private int finalPrice;

	@JsonProperty("discount_percent")
	private int discountPercent;
}
