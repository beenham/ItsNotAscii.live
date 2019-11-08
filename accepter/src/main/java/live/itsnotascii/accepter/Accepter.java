package live.itsnotascii.accepter;

import akka.actor.ActorSystem;
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
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class Accepter {
	public static final int PORT = 80;
	public static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

	public Accepter() {
	}

	private static String[] splitToNChar(String text, int size) {
		List<String> parts = new ArrayList<>();

		int length = text.length();
		for (int i = 0; i < length; i += size) {
			parts.add(text.substring(i, Math.min(length, i + size)));
		}
		return parts.toArray(new String[0]);
	}

	public void init() {
		ActorSystem system = ActorSystem.create();
		final Materializer materializer = ActorMaterializer.create(system);

		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<HttpRequest, HttpResponse>() {
					private final HttpResponse NOT_FOUND =
							HttpResponse.create()
									.withStatus(404)
									.withEntity("Unknown resource!\n");

					@Override
					public HttpResponse apply(HttpRequest request) throws Exception {
						Uri uri = request.getUri();

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
								return
										HttpResponse.create()
												.withEntity(ContentTypes.TEXT_PLAIN_UTF8,
														CLEAR_SCREEN + "ItsNotAscii.live\n");
							} else if (uri.path().equals("/hello")) {
								String name = uri.query().get("name").orElse("Mister X");

								return
										HttpResponse.create()
												.withEntity(CLEAR_SCREEN + "Hello " + name + "!\n");
							} else if (uri.path().equals("/ping")) {
								return HttpResponse.create().withEntity(CLEAR_SCREEN + "PONG!\n");
							} else {
								return NOT_FOUND;
							}
						} else {
							return NOT_FOUND;
						}
					}
				};


		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(system).bind(ConnectHttp.toHost("localhost", PORT), materializer);
		CompletionStage<ServerBinding> serverBindingFuture =
				serverSource.to(Sink.foreach(connection -> {
					System.out.println("Accepted new connection from " + connection.remoteAddress());

					connection.handleWithSyncHandler(requestHandler, materializer);
				})).run(materializer);
	}
}
