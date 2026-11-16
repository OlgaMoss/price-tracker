package io.github.solar.parser.exception;

/**
 * Ошибка неполных данных цены Steam, исключающая публикацию недостоверного наблюдения.
 */
public class IncompleteSteamPriceException extends RuntimeException {

	public IncompleteSteamPriceException(String message) {
		super(message);
	}
}
