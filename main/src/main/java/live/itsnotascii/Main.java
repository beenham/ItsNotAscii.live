package live.itsnotascii;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.Location;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.accepter.Accepter;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.messages.RegisterRequest;
import live.itsnotascii.messages.VideoRequest;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletionStage;

public class Main {
	private static final int WEB_PORT = 80;
	private static final String WEB_HOST = "127.0.0.1";
	private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

	public static void main(String[] args) {
		ActorSystem system = ActorSystem.create("ItsNotAsciiMain", ConfigFactory.load());

		ActorRef listener = system.actorOf(Props.create(Accepter.class), "MainListener");

		final Materializer materializer = Materializer.createMaterializer(system);

		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<HttpRequest, HttpResponse>() {
					private final HttpResponse NOT_FOUND =
							HttpResponse.create()
									.withStatus(404)
									.withEntity("Unknown resource!\n");

					@Override
					public HttpResponse apply(HttpRequest request) {
						Uri uri = request.getUri();

						if (request.getHeaders() != null
								&& request.getHeader(Constants.HTTP_HEADER_CACHE_REGISTER).isPresent()) {
							System.out.println("Ping Received Cache Register Request");
							request.getHeaders().forEach(System.out::println);
							System.out.println(request.getHeader(Constants.HTTP_HEADER_CACHE_REGISTER).get().value());
							listener.tell(new RegisterRequest.Cache(request.getHeader(Constants.HTTP_HEADER_CACHE_REGISTER).get().value()), ActorRef.noSender());
						}

						if (request.getHeaders() != null
								&& request.getHeader("user-agent").isPresent()
								&& !request.getHeader("user-agent").toString().contains("curl")) {
							return HttpResponse.create()
									.withStatus(302)
									.withEntity("You fool, you should be using this with curl!")
									.addHeader(Location.create("https://github.com/beenham/itsnotascii.live"));
						}

						if (request.method() == HttpMethods.GET) {
							if (uri.path().equals("/")) {
								return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8,
										CLEAR_SCREEN + "ItsNotAscii.live\n");
							} else if (uri.path().equals("/thetragedy")) {
								long id = new Random().nextLong();
								listener.tell(new VideoRequest.Available(id, "thetragedy"), ActorRef.noSender());
								long start = System.currentTimeMillis();
								while (!Accepter.requests.containsKey(id) && System.currentTimeMillis() - start < 3000) {

								}
								UnicodeVideo v = Accepter.requests.getOrDefault(id, null);
								if (v == null)
									return NOT_FOUND;

								int FPS = 24;
								int framelength = v.getFrames().size();
								long duration = framelength < 24 ? 1 : framelength / FPS;

								Source<ByteString, NotUsed> source = Source.range(0, framelength - 1)
										.map(str -> ByteString.fromString(v.getFrames().get(str) + "\n"))
										.throttle(framelength, Duration.ofSeconds(duration * 4));

								return HttpResponse.create()
										.withEntity(HttpEntities.createChunked(ContentTypes.TEXT_PLAIN_UTF8, source));


							} else {
								return NOT_FOUND;
							}
						} else {
							return NOT_FOUND;
						}
					}
				};


		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(system.classicSystem()).bind(ConnectHttp.toHost(WEB_HOST, WEB_PORT), materializer);

		serverSource.to(Sink.foreach(connection -> {
			System.out.println("Accepted new connection from " + connection.remoteAddress());

			connection.handleWithSyncHandler(requestHandler, materializer);
		})).run(materializer);
	}
}
