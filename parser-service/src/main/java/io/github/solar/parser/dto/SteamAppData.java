package io.github.solar.parser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO данных приложения из ответа Steam Store API (блок {@code data}).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamAppData {

	private String name;

	@JsonProperty("steam_appid")
	private Integer steamAppId;

	@JsonProperty("is_free")
	private boolean free;

	@JsonProperty("price_overview")
	private SteamPriceOverview priceOverview;
}
