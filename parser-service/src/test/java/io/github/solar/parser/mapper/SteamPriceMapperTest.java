package io.github.solar.parser.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;
import io.github.solar.parser.exception.IncompleteSteamPriceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
	 * Проверяет классификацию отсутствующего блока цены как ошибки неполных данных.
	 */
	@Test
	void shouldRejectIncompletePrice_WhenPriceOverviewMissingForPaidGame() {
		// Given — подготовка данных и моков
		SteamAppData appData = new SteamAppData();
		appData.setName("Unknown Game");
		appData.setSteamAppId(999);
		appData.setFree(false);

		// When — выполнение тестируемого действия
		Throwable error = catchThrowable(() -> SteamPriceMapper.toProductPriceMessage(appData));

		// Then — проверка результатов
		assertThat(error).isNotNull().hasMessageContaining("price_overview");
		assertThat(error).isInstanceOf(IncompleteSteamPriceException.class);
	}

	/**
	 * Проверяет отклонение синтетических неполных платных ответов без подстановки цены.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"null | price_overview",
			"{} | final",
			"{\"currency\":\"USD\"} | final",
			"{\"final\":null} | final"
	})
	void shouldRejectIncompletePrice_WhenPaidOverviewIncomplete(String overview, String reason) throws Exception {
		// Given — синтетический JSON, не подтверждённая фикстура Steam
		SteamAppData data = new ObjectMapper().readValue(
				"{\"steam_appid\":220,\"is_free\":false,\"price_overview\":" + overview + "}",
				SteamAppData.class);

		// When — маппинг неполного наблюдения
		Throwable error = catchThrowable(() -> SteamPriceMapper.toProductPriceMessage(data));

		// Then — ошибка отличается от штатного отсутствия данных
		assertThat(error).isNotNull().hasMessageContaining(reason);
		assertThat(error).isInstanceOf(IncompleteSteamPriceException.class);
	}

	/**
	 * Проверяет допустимость явного нуля и положительной цены без изменения бесплатности.
	 */
	@ParameterizedTest
	@CsvSource({"0, 0.00", "499, 4.99"})
	void shouldMapExplicitMonetaryPrice_WhenGameExplicitlyPaid(int finalValue, String expected) throws Exception {
		// Given — синтетический ответ с явно переданной денежной ценой
		SteamAppData data = new ObjectMapper().readValue(
				"{\"steam_appid\":220,\"is_free\":false,\"price_overview\":{\"final\":" + finalValue + "}}",
				SteamAppData.class);

		// When — маппинг цены
		ProductPriceMessage message = SteamPriceMapper.toProductPriceMessage(data).orElseThrow();

		// Then — цена сохранена, исходная бесплатность не изменена
		assertThat(message.getPrice()).isEqualByComparingTo(expected);
		assertThat((Object) data.getFree()).isEqualTo(Boolean.FALSE);
	}

	/**
	 * Проверяет бесплатную игру с отсутствующим или null блоком цены.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"", ",\"price_overview\":null"})
	void shouldMapFreePrice_WhenOverviewMissingOrNull(String overviewProperty) throws Exception {
		// Given — синтетический ответ с явным признаком бесплатности
		SteamAppData data = new ObjectMapper().readValue(
				"{\"steam_appid\":570,\"is_free\":true" + overviewProperty + "}", SteamAppData.class);

		// When — маппинг бесплатной игры
		ProductPriceMessage message = SteamPriceMapper.toProductPriceMessage(data).orElseThrow();

		// Then — блок цены не требуется
		assertThat(message.getPrice()).isEqualByComparingTo("0.00");
		assertThat((Object) data.getFree()).isEqualTo(Boolean.TRUE);
	}
}
