package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.Listener;
import live.itsnotascii.core.Event;
import live.itsnotascii.core.JoinCluster;
import live.itsnotascii.util.Args;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class CacheMain {
	private static final int WEB_PORT = 80;
	private static final String WEB_HOST = "127.0.0.1";

	public static void main(String... inputArgs) {
		Config cfg = ConfigFactory.load();
		Args args = parseArgs(cfg, inputArgs);
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("akka.remote.artery.canonical.port", args.port);
		overrides.put("akka.remote.artery.canonical.hostname", args.host);
		overrides.put("akka.remote.artery.bind.port", args.port);
		overrides.put("akka.remote.artery.bind.hostname", args.host);

		ActorSystem<Event> system = ActorSystem.create(Listener.create("Listener" ),
				args.name, ConfigFactory.parseMap(overrides).withFallback(cfg));

		final Materializer materializer = Materializer.createMaterializer(system);
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n" );

					@Override
					public HttpResponse apply(HttpRequest req) {
						if (req.getHeaders() != null) {
							if (req.getHeader(Cache.REGISTER_ACCEPT).isPresent()) {
								system.tell(new JoinCluster(req.getHeader(Cache.REGISTER_ACCEPT).get().value()));
							}
						}

						return NOT_FOUND;
					}
				};

		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(system.classicSystem()).bind(ConnectHttp.toHost(args.webHost, args.webPort));

		serverSource.to(Sink.foreach(c -> {
			System.out.println("Accepted new connection from " + c.remoteAddress());
			c.handleWithSyncHandler(requestHandler, materializer);
		})).run(materializer);

		Http http = Http.get(system.classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(Uri.create(args.target))
				.addHeader(HttpHeader.parse(Cache.REGISTER_REQUEST, "http://" + args.webHost + ":" + args.webPort));
		http.singleRequest(request);
	}

	private static Args parseArgs(Config cfg, String... args) {
		String webHost = WEB_HOST;
		int webPort = WEB_PORT;
		String sysHost = cfg.getString("akka.remote.artery.canonical.hostname" );
		int sysPort = cfg.getInt("akka.remote.artery.canonical.port" );
		String sysName = "ItsNotAscii";
		String target = "http://localhost";

		//	Parsing arguments
		//	TODO: use jcommander ¬.¬
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-wh":
					webHost = args[++i];
					break;
				case "-wp":
					webPort = Integer.parseInt(args[++i]);
					break;
				case "-h":
					sysHost = args[++i];
					break;
				case "-p":
					sysPort = Integer.parseInt(args[++i]);
					break;
				case "-n":
					sysName = args[++i];
					break;
				case "-t":
					target = args[++i];
					break;
				default:
					System.out.println("Unknown flag: " + args[i] + "\n" + "TODO Args\n" );
					System.exit(0);
			}
		}

		return new Args(webHost, sysHost, sysName, webPort, sysPort, target);
	}
}
