package io.github.solar.parser.exception;

/**
 * Исключение при ошибке обращения к Steam Store API.
 */
public class SteamApiException extends RuntimeException {

	public SteamApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public SteamApiException(String message) {
		super(message);
	}
}
