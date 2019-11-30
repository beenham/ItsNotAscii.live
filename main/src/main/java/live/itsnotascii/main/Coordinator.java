package live.itsnotascii.main;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
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
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.util.Arguments;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class Coordinator extends AbstractBehavior<Command> {
	private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

	private final String id;
	private final ActorRef<CacheManager.Command> cacheManager;
	private final Map<Long, UnicodeVideo> responses;

	private Coordinator(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		this.responses = new HashMap<>();
		context.getLog().info("I am alive! {}", context.getSelf());

		this.cacheManager = context.spawn(CacheManager.create("CacheManager"), "CacheManager");

		setupHttpListener();
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(c -> new Coordinator(c, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(RegisterRequest.class, this::onRegisterRequest)
				.onMessage(CacheManager.RespondVideo.class, this::onResponseVideo)
				.onMessage(CacheManager.RequestVideo.class, this::onRequestVideo)
				.onMessage(CacheManager.Test.class, this::test)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Coordinator onRegisterRequest(RegisterRequest req) {
		System.out.println(getContext().getSystem().name());
		System.out.println(getContext().getSystem().path());

		Http http = Http.get(getContext().getSystem().classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(req.sender)
				.addHeader(HttpHeader.parse(Constants.REGISTER_ACCEPT, req.location));
		http.singleRequest(request);

		System.out.println("Sending register request: " + req.location + " to " + req.sender);
		return this;
	}

	private Coordinator test(CacheManager.Test r) {
		this.cacheManager.tell(new CacheManager.RequestVideo(getContext().getSelf().narrow(), "test"));
		return this;
	}

	private Coordinator onResponseVideo(CacheManager.RespondVideo respondVideo) {
		if (respondVideo.getVideo() instanceof CacheManager.WrappedVideo) {
			CacheManager.WrappedVideo wrappedVideo = (CacheManager.WrappedVideo) respondVideo.getVideo();
			responses.put(respondVideo.getRequestId(), wrappedVideo.getVideo());
			getContext().getSystem().log().info("Cache Video Response received video with request ID {}", respondVideo.getRequestId());
			getContext().getSystem().log().info("Video: " + String.join(", ", wrappedVideo.getVideo().getFrames()));
		} else {
			responses.put(respondVideo.getRequestId(), null);
			if (respondVideo.getVideo() instanceof CacheManager.VideoNotFound) {
				getContext().getSystem().log().info("Cache Video Response for request ID {}, VideoNotFound", respondVideo.getRequestId());
			} else if (respondVideo.getVideo() instanceof CacheManager.CacheTimedOut) {
				getContext().getSystem().log().info("Cache Video Response for request ID {}, Timeout", respondVideo.getRequestId());
			} else {
				getContext().getSystem().log().info("Cache Video Response for request ID {}, Unknown type");
			}
		}

		return this;
	}

	private Coordinator onRequestVideo(CacheManager.RequestVideo requestVideo) {
		this.cacheManager.tell(requestVideo);
		return this;
	}

	private Coordinator onPostStop() {
		getContext().getLog().info("Listener {} stopped.", this.id);
		return this;
	}

	private void setupHttpListener() {
		Arguments args = Arguments.get();
		ActorContext<Command> context = getContext();
		//	Setting up HTTP listener
		final Materializer materializer = Materializer.createMaterializer(context.getSystem());
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity("Unknown resource!\n");

					@Override
					public HttpResponse apply(HttpRequest req) throws Exception {
						Uri uri = req.getUri();

						if (req.getHeaders() != null) {
							if (req.getHeader(Constants.REGISTER_REQUEST).isPresent()) {
								String sendTo = req.getHeader(Constants.REGISTER_REQUEST).get().value();
								String location = String.format("%s:%d", args.getHostname(), args.getPort());
								context.getSelf().tell(new Coordinator.RegisterRequest(sendTo, location));
							}

							if (req.getHeader("user-agent").isPresent()
									&& !req.getHeader("user-agent").toString().contains("curl"))
								return HttpResponse.create()
										.withStatus(302)
										.withEntity("You fool, you should be using this with curl!")
										.addHeader(Location.create("https://github.com/beenham/itsnotascii.live"));
						}

						if (req.method() == HttpMethods.GET) {
							if (uri.path().equals("/")) {
								context.getSelf().tell(new CacheManager.Test("Sad Face :("));
								return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8,
										CLEAR_SCREEN + "Welcome to ItsNotAscii.live!\n");
							} else if (uri.path().startsWith("/")) {
								String videoCode = uri.path().substring(1);
								getContext().getSystem().log().info("HTTP Video Request {}", videoCode);
								CacheManager.RequestVideo requestVideo = new CacheManager.RequestVideo(getContext().getSelf().narrow(), videoCode);

								context.getSelf().tell(requestVideo);

								long requestTime = System.currentTimeMillis();
								long currentTime = System.currentTimeMillis();
								while (!responses.containsKey(requestVideo.getRequestId()) && 10000 > currentTime - requestTime) {
									currentTime = System.currentTimeMillis();
									Thread.sleep(500);
								}

								UnicodeVideo video = responses.getOrDefault(requestVideo.getRequestId(), null);

								if (video != null) {
									int FPS = 24;
									int frameLength = video.getFrames().size();
									long duration = frameLength < 24 ? 1 : frameLength / FPS;

									Source<ByteString, NotUsed> source = Source.range(0, frameLength - 1)
											.map(str -> ByteString.fromString(video.getFrames().get(str) + "\n"))
											.throttle(frameLength, Duration.ofSeconds(duration * 4));

									return HttpResponse.create()
											.withEntity(HttpEntities.createChunked(ContentTypes.TEXT_PLAIN_UTF8, source));
								} else {
									return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8,
											CLEAR_SCREEN + "Video Not Found :(\n");
								}

							}
						}
						return NOT_FOUND;
					}
				};

		Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource =
				Http.get(context.getSystem().classicSystem()).bind(ConnectHttp.toHost(args.getWebHostname(), args.getWebPort()));

		serverSource.to(Sink.foreach(c -> {
			System.out.println("Accepted new connection from " + c.remoteAddress());
			c.handleWithSyncHandler(requestHandler, materializer);
		})).run(materializer);
	}

	public static class RegisterRequest implements Command {
		private final String sender;
		private final String location;

		public RegisterRequest(String sender, String location) {
			this.sender = sender;
			this.location = location;
		}
	}
}
