package live.itsnotascii.core;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

public abstract class CommonMain {
	private static final String TITLE =
			"██╗████████╗███████╗███╗   ██╗ ██████╗ ████████╗ █████╗ ███████╗ ██████╗██╗██╗   ██╗     ██╗██╗   ██╗███████╗\n" +
			"██║╚══██╔══╝██╔════╝████╗  ██║██╔═══██╗╚══██╔══╝██╔══██╗██╔════╝██╔════╝██║██║   ██║     ██║██║   ██║██╔════╝\n" +
			"██║   ██║   ███████╗██╔██╗ ██║██║   ██║   ██║   ███████║███████╗██║     ██║██║   ██║     ██║██║   ██║█████╗  \n" +
			"██║   ██║   ╚════██║██║╚██╗██║██║   ██║   ██║   ██╔══██║╚════██║██║     ██║██║   ██║     ██║╚██╗ ██╔╝██╔══╝  \n" +
			"██║   ██║   ███████║██║ ╚████║╚██████╔╝   ██║   ██║  ██║███████║╚██████╗██║██║██╗███████╗██║ ╚████╔╝ ███████╗\n" +
			"╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═══╝ ╚═════╝    ╚═╝   ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═╝╚═╝╚═╝╚══════╝╚═╝  ╚═══╝  ╚══════╝";

	private static final String APPLICATION_CONFIG = "application.conf";

	public static Arguments parseInputArgs(String subTitle, String... inputArgs) {
		JCommander commander = JCommander.newBuilder()
				.addObject(Arguments.get())
				.build();

		try {
			commander.parse(inputArgs);
			Arguments args = Arguments.get();

			//	font: ANSI Shadow
			Log.wtf("MAIN", "\n\n" + TITLE + "\n\n" + (subTitle == null ? "" : subTitle + "\n\n") + args);

			return args;
		} catch (ParameterException ignored) {
			commander.usage();
			return null;
		}
	}

	public static Config loadConfig() {
		return ConfigFactory.load(APPLICATION_CONFIG);
	}
}
