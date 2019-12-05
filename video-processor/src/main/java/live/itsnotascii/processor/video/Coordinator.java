package live.itsnotascii.processor.video;

import akka.actor.AddressFromURIString;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.JoinSeedNodes;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.core.messages.JoinCluster;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

import java.util.Collections;
import java.util.concurrent.CompletionStage;

public class Coordinator extends AbstractBehavior<Command> {
	private static final String TAG = Coordinator.class.getCanonicalName();
	private final String id;

	private Coordinator(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;

		Log.i(TAG, String.format("Listener (%s) started", getContext().getSelf()));
		context.spawn(VideoProcessor.create(), "VideoProcessor");
		setupHttpListener();
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(c -> new Coordinator(c, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(JoinCluster.class, this::onJoinCluster)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Coordinator onJoinCluster(JoinCluster r) {
		String location = getContext().getSystem() + "@" + r.getLocation();

		Cluster cluster = Cluster.get(getContext().getSystem());
		cluster.manager().tell(new JoinSeedNodes(
				Collections.singletonList(AddressFromURIString.parse(location))));
		return this;
	}

	private Coordinator onPostStop() {
		Log.i(TAG, String.format("Listener %s stopped.", this.id));
		return this;
	}

	private void setupHttpListener() {
		Arguments args = Arguments.get();
		ActorContext<Command> context = getContext();

		// Setting up HTTP Listener
		final Materializer materializer = Materializer.createMaterializer(context.getSystem());
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n");

					@Override
					public HttpResponse apply(HttpRequest req) {
						if (req.getHeaders() != null) {
							if (req.getHeader(Constants.REGISTER_ACCEPT).isPresent()) {
								context.getSelf().tell(new JoinCluster(req.getHeader(Constants.REGISTER_ACCEPT).get().value()));
							}
						}

						return NOT_FOUND;
					}
				};

		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(context.getSystem().classicSystem()).bind(ConnectHttp.toHost(args.getWebHostname(), args.getWebPort()));

		serverSource.to(Sink.foreach(c -> {
			Log.v(TAG, String.format("Accepted new connection from %s", c.remoteAddress()));
			c.handleWithSyncHandler(requestHandler, materializer);
		})).run(materializer);
	}
}