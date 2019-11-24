package live.itsnotascii.util;

public class Args {
	public final String webHost, host, name;
	public final int webPort, port;

	public Args(String webHost, String host, String name, int webPort, int port) {
		this.webHost = webHost;
		this.host = host;
		this.name = name;
		this.webPort = webPort;
		this.port = port;
	}
}
