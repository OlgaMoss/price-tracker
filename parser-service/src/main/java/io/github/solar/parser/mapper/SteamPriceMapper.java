package io.github.solar.parser.mapper;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Маппер ответа Steam Store API в Kafka-сообщение {@link ProductPriceMessage}.
 */
public final class SteamPriceMapper {

	private static final String STEAM_STORE_URL_TEMPLATE = "https://store.steampowered.com/app/%d";
	private static final String EXTERNAL_ID_TEMPLATE = "steam-%d";

	private SteamPriceMapper() {
	}

	/**
	 * Преобразует данные приложения Steam в сообщение для топика {@code raw-prices}.
	 *
	 * @param appData данные из Steam Store API
	 * @return сообщение о цене или пустой результат, если цену определить нельзя
	 */
	public static Optional<ProductPriceMessage> toProductPriceMessage(SteamAppData appData) {
		if (appData == null || appData.getSteamAppId() == null) {
			return Optional.empty();
		}

		Optional<BigDecimal> priceOpt = resolvePrice(appData);
		if (priceOpt.isEmpty()) {
			return Optional.empty();
		}

		int appId = appData.getSteamAppId();
		return Optional.of(ProductPriceMessage.builder()
				.externalId(EXTERNAL_ID_TEMPLATE.formatted(appId))
				.title(appData.getName())
				.price(priceOpt.get())
				.url(STEAM_STORE_URL_TEMPLATE.formatted(appId))
				.build());
	}

	private static Optional<BigDecimal> resolvePrice(SteamAppData appData) {
		if (appData.isFree()) {
			return Optional.of(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		}

		SteamPriceOverview overview = appData.getPriceOverview();
		if (overview == null) {
			return Optional.empty();
		}

		// Steam возвращает цену в копейках — конвертируем в рубли
		return Optional.of(BigDecimal.valueOf(overview.getFinalPrice(), 2));
	}
}
