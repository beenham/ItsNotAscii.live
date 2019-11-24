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
import live.itsnotascii.util.Args;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class CacheMain {
	private static final int WEB_PORT = 80;
	private static final String WEB_HOST = "127.0.0.1";

	public static void main(String... inputArgs) {
		Config cfg = ConfigFactory.load();

		String webHost = WEB_HOST;
		int webPort = WEB_PORT;
		String sysHost = cfg.getString("akka.remote.artery.canonical.hostname" );
		int sysPort = cfg.getInt("akka.remote.artery.canonical.port" );
		String sysName = "ItsNotAscii";
		String target = "http://127.0.0.1";

		//	Parsing arguments
		//	TODO: use jcommander ¬.¬
		for (int i = 0; i < inputArgs.length; i++) {
			switch (inputArgs[i]) {
				case "-wh":
					webHost = inputArgs[++i];
					break;
				case "-wp":
					webPort = Integer.parseInt(inputArgs[++i]);
					break;
				case "-h":
					sysHost = inputArgs[++i];
					break;
				case "-p":
					sysPort = Integer.parseInt(inputArgs[++i]);
					break;
				case "-n":
					sysName = inputArgs[++i];
					break;
				case "-t":
					target = inputArgs[++i];
					break;
				default:
					System.out.println("Unknown flag: " + inputArgs[i] + "\n" + "TODO Args\n" );
					System.exit(0);
			}
		}

		final Args args = new Args(webHost, sysHost, sysName, webPort, sysPort);
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
								system.tell(new Cache.Init("Cache", req.getHeader(Cache.REGISTER_ACCEPT).get().value()));
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
				.withUri(Uri.create(target))
				.addHeader(HttpHeader.parse(Cache.REGISTER_REQUEST, "http://" + args.webHost + ":" + args.webPort));
		http.singleRequest(request);
	}
}
