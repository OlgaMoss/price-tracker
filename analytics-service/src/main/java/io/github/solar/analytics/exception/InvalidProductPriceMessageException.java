package io.github.solar.analytics.exception;

import lombok.Getter;

/**
 * Постоянная ошибка содержимого сообщения о цене, отличимая от инфраструктурного отказа.
 */
@Getter
public class InvalidProductPriceMessageException extends RuntimeException {

	private final String field;

	/**
	 * Создаёт ошибку с именем поля и причиной отказа без включения исходного содержимого.
	 *
	 * @param field некорректное поле сообщения
	 * @param reason понятная причина отказа
	 */
	public InvalidProductPriceMessageException(String field, String reason) {
		super(field + ": " + reason);
		this.field = field;
	}
}
