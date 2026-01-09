package de.haumacher.autotranslate.log;

/**
 * Simple logging interface for translation utilities.
 *
 * <p>
 * Allows translation code to log messages without depending on a specific
 * logging framework, enabling integration with both CLI (System.out) and
 * Maven plugin (Maven Log) contexts.
 * </p>
 */
public interface Logger {

	/**
	 * Logs an informational message.
	 *
	 * @param message The message to log
	 */
	void info(String message);

	/**
	 * Logs a warning message.
	 *
	 * @param message The message to log
	 */
	void warn(String message);

	/**
	 * Logs an error message.
	 *
	 * @param message The message to log
	 */
	void error(String message);
}
