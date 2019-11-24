package live.itsnotascii;

import akka.actor.AddressFromURIString;
import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.cluster.typed.JoinSeedNodes;
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
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.cache.Listener;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.Event;

import java.util.Collections;
import java.util.concurrent.CompletionStage;

public class Main {
	private static final int WEB_PORT = 81;
	private static final String WEB_HOST = "127.0.0.1";

	private static Config clusterConfig =
			ConfigFactory.parseString(
					"akka { \n"
							+ "  actor.provider = cluster \n"
							+ "  remote.artery { \n"
							+ "    canonical { \n"
							+ "      hostname = \"127.0.0.1\" \n"
							+ "      port = 25521 \n"
							+ "    } \n"
							+ "  } \n"
							+ "}  \n");

	private static Config noPort =
			ConfigFactory.parseString(
					"      akka.remote.classic.netty.tcp.port = 25521 \n"
							+ "      akka.remote.artery.canonical.port = 25521 \n");

	public static void main(String... args) {
		ActorSystem<Event> system = ActorSystem.create(Listener.create("Listener" ),
				"ItsNotAscii", noPort.withFallback(clusterConfig));


//		Cluster cluster = Cluster.get(system);
//		cluster.manager().tell(new JoinSeedNodes(
//				Collections.singletonList(AddressFromURIString.parse("akka://ItsNotAscii@127.0.0.1:25520"))));

		final Materializer materializer = Materializer.createMaterializer(system);
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n" );

					@Override
					public HttpResponse apply(HttpRequest r) {
						Uri uri = r.getUri();

						System.out.println(r);

						if (r.getHeaders() != null) {
							System.out.println("Headers not empty");
							if (r.getHeader(Cache.REGISTER_ACCEPT).isPresent()) {
								System.out.println("Yeet");
								system.tell(new Cache.Init("Cache", r.getHeader(Cache.REGISTER_ACCEPT).get().value()));
							}
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



		Http http = Http.get(system.classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(Uri.create("http://127.0.0.1"))
				.addHeader(HttpHeader.parse(Cache.REGISTER_REQUEST, "http://"+WEB_HOST+":"+WEB_PORT));
		http.singleRequest(request);

		System.out.println("Sent: " + request);
	}
}
