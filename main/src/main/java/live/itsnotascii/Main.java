package live.itsnotascii;

import akka.actor.typed.ActorSystem;
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
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Event;
import live.itsnotascii.main.Listener;

import java.util.concurrent.CompletionStage;

public class Main {
	private static final int WEB_PORT = 80;
	private static final String WEB_HOST = "127.0.0.1";
	private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

	public static void main(String... args) {
		ActorSystem<Event> system = ActorSystem.create(Listener.create("MainListener"), "ItsNotAscii", ConfigFactory.load());

		system.tell(new Listener.InitCacheManager("CacheManager"));

		final Materializer materializer = Materializer.createMaterializer(system);
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n");

					@Override
					public HttpResponse apply(HttpRequest r) throws Exception {
						Uri uri = r.getUri();

						if (r.getHeaders() != null
								&& r.getHeader("user-agent").isPresent()
								&& !r.getHeader("user-agent").toString().contains("curl"))
							return HttpResponse.create()
									.withStatus(302)
									.withEntity("You fool, you should be using this with curl!")
									.addHeader(Location.create("https://github.com/beenham/itsnotascii.live"));

						if (r.method() == HttpMethods.GET) {
							if (uri.path().equals("/"))
								return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8,
										CLEAR_SCREEN + "Welcome to ItsNotAscii.live!\n");
						}

						return NOT_FOUND;
					}
				};

		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(system.classicSystem()).bind(ConnectHttp.toHost(WEB_HOST, WEB_PORT), materializer);

		serverSource.to(Sink.foreach(c -> {
			System.out.println("Accepted new connection from " + c.remoteAddress());
			c.handleWithSyncHandler(requestHandler, materializer);
		})).run(materializer);
	}
}
