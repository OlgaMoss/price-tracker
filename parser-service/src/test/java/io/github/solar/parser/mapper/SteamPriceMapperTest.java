package io.github.solar.parser.mapper;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты маппера {@link SteamPriceMapper}.
 */
@DisplayName("SteamPriceMapper")
class SteamPriceMapperTest {

	/**
	 * Проверяет конвертацию платного товара со скидкой из центов в доллары.
	 */
	@Test
	void shouldMapPaidProduct_WhenPriceOverviewPresent() {
		// Given — подготовка данных и моков
		SteamAppData appData = new SteamAppData();
		appData.setName("Half-Life 2");
		appData.setSteamAppId(220);
		appData.setFree(false);

		SteamPriceOverview overview = new SteamPriceOverview();
		overview.setCurrency("USD");
		overview.setInitial(999);
		overview.setFinalPrice(499);
		overview.setDiscountPercent(50);
		appData.setPriceOverview(overview);

		// When — выполнение тестируемого действия
		Optional<ProductPriceMessage> result = SteamPriceMapper.toProductPriceMessage(appData);

		// Then — проверка результатов
		assertThat(result).isPresent();
		ProductPriceMessage message = result.get();
		assertThat(message.getExternalId()).isEqualTo("steam-220");
		assertThat(message.getTitle()).isEqualTo("Half-Life 2");
		assertThat(message.getPrice()).isEqualByComparingTo("4.99");
		assertThat(message.getUrl()).isEqualTo("https://store.steampowered.com/app/220");
	}

	/**
	 * Проверяет маппинг бесплатной игры в нулевую цену.
	 */
	@Test
	void shouldMapZeroPrice_WhenGameIsFree() {
		// Given — подготовка данных и моков
		SteamAppData appData = new SteamAppData();
		appData.setName("Dota 2");
		appData.setSteamAppId(570);
		appData.setFree(true);

		// When — выполнение тестируемого действия
		Optional<ProductPriceMessage> result = SteamPriceMapper.toProductPriceMessage(appData);

		// Then — проверка результатов
		assertThat(result).isPresent();
		assertThat(result.get().getPrice()).isEqualByComparingTo("0.00");
	}

	/**
	 * Проверяет пропуск товара без блока цены.
	 */
	@Test
	void shouldReturnEmpty_WhenPriceOverviewMissingForPaidGame() {
		// Given — подготовка данных и моков
		SteamAppData appData = new SteamAppData();
		appData.setName("Unknown Game");
		appData.setSteamAppId(999);
		appData.setFree(false);

		// When — выполнение тестируемого действия
		Optional<ProductPriceMessage> result = SteamPriceMapper.toProductPriceMessage(appData);

		// Then — проверка результатов
		assertThat(result).isEmpty();
	}
}
