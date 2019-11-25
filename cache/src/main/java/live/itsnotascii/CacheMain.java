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
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.Listener;
import live.itsnotascii.core.Event;
import live.itsnotascii.core.JoinCluster;
import live.itsnotascii.util.Arguments;

import java.util.concurrent.CompletionStage;

public class CacheMain {
	public static void main(String... inputArgs) {
		JCommander commander = JCommander.newBuilder()
				.addObject(Arguments.get())
				.build();

		try {
			commander.parse(inputArgs);
			Arguments args = Arguments.get();
			Config cfg = ConfigFactory.load();

			//	Creating Actor System
			ActorSystem<Event> system = ActorSystem.create(Listener.create("Listener"),
					args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

			//	Setting up HTTP Listener
			final Materializer materializer = Materializer.createMaterializer(system);
			final Function<HttpRequest, HttpResponse> requestHandler =
					new Function<>() {
						private final HttpResponse NOT_FOUND = HttpResponse.create()
								.withStatus(404)
								.withEntity("Unknown resource!\n");

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
					Http.get(system.classicSystem()).bind(ConnectHttp.toHost(args.getWebHostname(), args.getWebPort()));

			serverSource.to(Sink.foreach(c -> {
				System.out.println("Accepted new connection from " + c.remoteAddress());
				c.handleWithSyncHandler(requestHandler, materializer);
			})).run(materializer);

			//	Sending http request to target
			Http http = Http.get(system.classicSystem());
			HttpRequest request = HttpRequest.create()
					.withUri(Uri.create(args.getTarget()))
					.addHeader(HttpHeader.parse(Cache.REGISTER_REQUEST, "http://" + args.getWebHostname() +
							":" + args.getWebPort()));
			http.singleRequest(request);
		} catch (ParameterException ignored) {
			commander.usage();
		}
	}
}
