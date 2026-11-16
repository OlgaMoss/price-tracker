package io.github.solar.parser.mapper;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;
import io.github.solar.parser.exception.IncompleteSteamPriceException;

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
	 * @return сообщение о цене или пустой результат, если нет данных приложения или его ID
	 * @throws IncompleteSteamPriceException если без явной бесплатности не получена денежная цена
	 */
	public static Optional<ProductPriceMessage> toProductPriceMessage(SteamAppData appData) {
		if (appData == null || appData.getSteamAppId() == null) {
			return Optional.empty();
		}

		BigDecimal price = resolvePrice(appData);

		int appId = appData.getSteamAppId();
		return Optional.of(ProductPriceMessage.builder()
				.externalId(EXTERNAL_ID_TEMPLATE.formatted(appId))
				.title(appData.getName())
				.price(price)
				.url(STEAM_STORE_URL_TEMPLATE.formatted(appId))
				.build());
	}

	private static BigDecimal resolvePrice(SteamAppData appData) {
		if (Boolean.TRUE.equals(appData.getFree())) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}

		SteamPriceOverview overview = appData.getPriceOverview();
		if (overview == null) {
			throw new IncompleteSteamPriceException("Отсутствует или равен null блок price_overview");
		}
		if (overview.getFinalPrice() == null) {
			throw new IncompleteSteamPriceException("Отсутствует или равно null поле price_overview.final");
		}

		// Steam возвращает цену в копейках — конвертируем в рубли
		return BigDecimal.valueOf(overview.getFinalPrice(), 2);
	}
}
