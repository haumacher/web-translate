package de.haumacher.autotranslate.log;

/**
 * Logger implementation that writes to System.out and System.err.
 *
 * <p>
 * Used for CLI execution where direct console output is appropriate.
 * </p>
 */
public class ConsoleLogger implements Logger {

	/**
	 * Singleton instance for convenience.
	 */
	public static final ConsoleLogger INSTANCE = new ConsoleLogger();

	@Override
	public void info(String message) {
		System.out.println(message);
	}

	@Override
	public void warn(String message) {
		System.err.println("WARN: " + message);
	}

	@Override
	public void error(String message) {
		System.err.println("ERROR: " + message);
	}
}
