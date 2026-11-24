package io.github.solar.analytics.validation;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.common.dto.ProductPriceMessage;

import java.math.BigDecimal;

/**
 * Проверяет входные данные аналитики до обращения к репозиториям, не изменяя Kafka DTO.
 */
public final class ProductPriceMessageValidator {

	private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
	private static final int REQUIRED_TEXT_MAX_LENGTH = 255;
	private static final int URL_MAX_LENGTH = 512;

	private ProductPriceMessageValidator() {
	}

	/**
	 * Проверяет обязательность полей, ограничения хранения и точность цены без округления.
	 *
	 * @param message входящее сообщение о цене
	 * @throws InvalidProductPriceMessageException если сообщение нарушает правила входных данных
	 */
	public static void validate(ProductPriceMessage message) {
		if (message == null) {
			throw new InvalidProductPriceMessageException("message", "сообщение обязательно");
		}

		validateText("externalId", message.getExternalId(), true, REQUIRED_TEXT_MAX_LENGTH);
		validateText("title", message.getTitle(), true, REQUIRED_TEXT_MAX_LENGTH);
		validatePrice(message.getPrice());
		validateText("url", message.getUrl(), false, URL_MAX_LENGTH);
	}

	private static void validateText(String field, String value, boolean required, int maxLength) {
		if (value == null) {
			if (required) {
				throw new InvalidProductPriceMessageException(field, "поле обязательно");
			}
			return;
		}
		if (required && value.isBlank()) {
			throw new InvalidProductPriceMessageException(field, "поле не должно быть пустым или состоять из пробелов");
		}
		if (value.codePointCount(0, value.length()) > maxLength) {
			throw new InvalidProductPriceMessageException(field, "длина не должна превышать " + maxLength + " символов");
		}
		if (value.indexOf('\0') >= 0) {
			throw new InvalidProductPriceMessageException(field, "поле не должно содержать символ NUL");
		}
	}

	private static void validatePrice(BigDecimal price) {
		if (price == null) {
			throw new InvalidProductPriceMessageException("price", "цена обязательна");
		}
		if (price.compareTo(BigDecimal.ZERO) < 0 || price.compareTo(MAX_PRICE) > 0) {
			throw new InvalidProductPriceMessageException("price", "цена должна быть от 0 до 99999999.99");
		}
		// Масштаб исходного значения может превышать 2 только за счёт завершающих нулей.
		if (price.stripTrailingZeros().scale() > 2) {
			throw new InvalidProductPriceMessageException("price", "цена должна представляться с масштабом 2 без округления");
		}
	}
}
