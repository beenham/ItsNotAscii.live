package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Location;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Event;
import live.itsnotascii.main.Listener;
import live.itsnotascii.util.Args;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class Main {
	private static final int WEB_PORT = 1264;
	private static final String WEB_HOST = "127.0.0.1";
	private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

	public static void main(String... inputArgs) {
		Config cfg = ConfigFactory.load();

		String webHost = WEB_HOST;
		int webPort = WEB_PORT;
		String sysHost = cfg.getString("akka.remote.artery.canonical.hostname" );
		int sysPort = cfg.getInt("akka.remote.artery.canonical.port" );
		String sysName = "ItsNotAscii";

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

		//	Creating Actor System
		ActorSystem<Event> system = ActorSystem.create(Listener.create("MainListener" ),
				args.name, ConfigFactory.parseMap(overrides).withFallback(cfg));

		//	Setting up HTTP listener
		final Materializer materializer = Materializer.createMaterializer(system);
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n" );

					@Override
					public HttpResponse apply(HttpRequest req) throws Exception {
						Uri uri = req.getUri();

						if (req.getHeaders() != null) {
							if (req.getHeader(Cache.REGISTER_REQUEST).isPresent()) {
								String sendTo = req.getHeader(Cache.REGISTER_REQUEST).get().value();
								String location = String.format("%s:%d", args.host, args.port);
								system.tell(new Listener.RegisterRequest(sendTo, location));
							}

							if (req.getHeader("user-agent" ).isPresent()
									&& !req.getHeader("user-agent" ).toString().contains("curl" ))
								return HttpResponse.create()
										.withStatus(302)
										.withEntity("You fool, you should be using this with curl!" )
										.addHeader(Location.create("https://github.com/beenham/itsnotascii.live" ));
						}

						if (req.method() == HttpMethods.GET) {
							if (uri.path().equals("/" )) {
								system.tell(new CacheManager.Test("Sad Face :("));
								return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8,
										CLEAR_SCREEN + "Welcome to ItsNotAscii.live!\n" );
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

		//	Initializing cluster
		Cluster cluster = Cluster.get(system);
		cluster.manager().tell(Join.create(cluster.selfMember().address()));

		//	Initializing managers
		system.tell(new CacheManager.Init("CacheManager" ));
	}
}
