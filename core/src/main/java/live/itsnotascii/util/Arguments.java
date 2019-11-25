package live.itsnotascii.util;

import com.beust.jcommander.Parameter;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Class used for arguments passed through into the program.
 */
public class Arguments {
	private static final Arguments INSTANCE = new Arguments();

	private Arguments() {}

	@Getter
	@Parameter(names = {"-wp", "--webport"}, description = "The port to bind the web-server listener.", order = 4)
	private Integer webPort = 80;

	@Getter
	@Parameter(names = {"-wb", "--webbind", "--webhost"},
			description = "The hostname/ip to bind the web-server listener", order = 3)
	private String webHostname = "127.0.0.1";

	@Getter
	@Parameter(names = {"-p", "--port"}, description = "The port to bind the Actor System.", order = 2)
	private Integer port = 25520;

	@Getter
	@Parameter(names = {"-b", "--bind", "--host"},
			description = "The hostname/ip to bind the Actor System", order = 1)
	private String hostname = "127.0.0.1";

	@Getter
	@Parameter(names = {"-n", "--name"}, description = "The name of the cluster system.", required = true, order = 0)
	private String name;

	@Getter
	@Parameter(names = {"-t", "--target"}, description = "The default target for the cluster.")
	private String target = "http://localhost";

	@Parameter(names = {"-h, --help"}, description = "Displays this message and terminates.", help = true)
	private Boolean help;

	public Map<String, Object> getOverrides() {
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("akka.remote.artery.canonical.port", port);
		overrides.put("akka.remote.artery.canonical.hostname", hostname);
		overrides.put("akka.remote.artery.bind.port", port);
		overrides.put("akka.remote.artery.bind.hostname", hostname);
		return overrides;
	}

	public static Arguments get() {
		return INSTANCE;
	}

	@Override
	public String toString() {
		return "System: " + name + "\n" +
				"Target: " + target + "\n" +
				"SystemServer: " + hostname + ":" + port + "\n" +
				"WebServer: " + webHostname + ":" + webPort + "\n";
	}
}