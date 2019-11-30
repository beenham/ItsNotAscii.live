package live.itsnotascii.util;

import static live.itsnotascii.util.Color.*;

public class Log {
	/**
	 * Used to log general excess messages that is not important or debug.
	 * Only prints when the program is running in verbose mode.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void v(String TAG, Object message) {
		if (Arguments.get().getVerbose()) {
			System.out.println(WHITE + "VERBOSE/" + TAG + " : " + message + RESET);
		}
	}

	/**
	 * Used to log messages that is considered for debugging.
	 * Only prints when the program is running in verbose mode.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void d(String TAG, Object message) {
		if (Arguments.get().getVerbose()) {
			System.out.println(WHITE_BOLD + "DEBUG/" + TAG + " : " + WHITE + message + RESET);
		}
	}

	/**
	 * Used to log information when an error occurs.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void e(String TAG, Object message) {
		System.out.println(RED_BOLD + "ERROR/" + TAG + " : " + RED + message + RESET);
	}

	/**
	 * Used to print important informational messages to console.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void i(String TAG, Object message) {
		System.out.println(BLUE_BOLD + "INFO/" + TAG + " : " + BLUE + message + RESET);
	}

	/**
	 * Used to send a warning. This should be used when something odd happens, but it's not necessarily an error.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void w(String TAG, Object message) {
		System.out.println(YELLOW_BOLD + "WARN/" + TAG + " : " + YELLOW + message + RESET);
	}

	/**
	 * What a Terrible Failure: Report a condition that should never happen.
	 *
	 * @param TAG {@code String}: Used to identify the source of a log message.
	 *                          It usually identifies the class or activity where the log call occurs.
	 * @param message {@code String}: The messaged to be logged.
	 */
	public static void wtf(String TAG, Object message) {
		System.out.println(PURPLE_BOLD + "WTF/" + TAG + " : " + PURPLE + message + RESET);
	}
}