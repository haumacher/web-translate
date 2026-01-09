package de.haumacher.autotranslate.maven;

import org.apache.maven.plugin.logging.Log;

import de.haumacher.autotranslate.log.Logger;

/**
 * Adapter that bridges the auto-translate Logger interface to Maven's Log interface.
 *
 * <p>
 * This allows translation utilities to log through Maven's logging system,
 * ensuring proper integration with Maven's build output formatting.
 * </p>
 */
public class MavenLoggerAdapter implements Logger {

	private final Log _mavenLog;

	/**
	 * Creates a new Maven logger adapter.
	 *
	 * @param mavenLog Maven's log instance from the Mojo
	 */
	public MavenLoggerAdapter(Log mavenLog) {
		_mavenLog = mavenLog;
	}

	@Override
	public void info(String message) {
		_mavenLog.info(message);
	}

	@Override
	public void warn(String message) {
		_mavenLog.warn(message);
	}

	@Override
	public void error(String message) {
		_mavenLog.error(message);
	}
}
