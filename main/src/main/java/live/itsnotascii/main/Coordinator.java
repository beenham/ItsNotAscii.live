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
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Location;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.Regex;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.core.messages.HttpResponses;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;

public class Coordinator extends AbstractBehavior<Command> {
	private static final String TAG = Coordinator.class.getCanonicalName();
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
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Coordinator onRegisterRequest(RegisterRequest req) {
		Log.v(TAG, String.format("Register request from %s", req.sender));

		Http http = Http.get(getContext().getSystem().classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(req.sender)
				.addHeader(HttpHeader.parse(Constants.REGISTER_ACCEPT, req.location));
		http.singleRequest(request);

		Log.v(TAG, String.format("Sent register accept (%s) to %s", req.location, req.sender));
		return this;
	}

	private Coordinator onResponseVideo(CacheManager.RespondVideo respondVideo) {
		if (respondVideo.getVideo() instanceof CacheManager.WrappedVideo) {
			CacheManager.WrappedVideo wrappedVideo = (CacheManager.WrappedVideo) respondVideo.getVideo();
			responses.put(respondVideo.getRequestId(), wrappedVideo.getVideo());
			getContext().getLog().info("Cache Video Response received video for #{}", respondVideo.getRequestId());
		} else {
			responses.put(respondVideo.getRequestId(), null);
			if (respondVideo.getVideo() instanceof CacheManager.VideoNotFound) {
				getContext().getLog().info("Cache Video Response for #{}, VideoNotFound", respondVideo.getRequestId());
			} else if (respondVideo.getVideo() instanceof CacheManager.CacheTimedOut) {
				getContext().getLog().info("Cache Video Response for #{}, Timeout", respondVideo.getRequestId());
			} else {
				getContext().getLog().info("Cache Video Response is Unknown type");
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
							.withEntity(CLEAR_SCREEN + "\n" + HttpResponses.NOT_FOUND);

					private final HttpResponse INVALID_LINK = HttpResponse.create()
							.withStatus(404)
							.withEntity(CLEAR_SCREEN + "\n" + HttpResponses.INVALID_URL);

					private final HttpResponse WELCOME = HttpResponse.create()
							.withStatus(404)
							.withEntity(CLEAR_SCREEN + "\n" + HttpResponses.WELCOME_SCREEN);

					@Override
					public HttpResponse apply(HttpRequest req) {
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
								return WELCOME;
							} else if (Cache.INTERNAL_VIDEOS.contains(uri.path().substring(1))) {
								return getHttpResponse(new CacheManager.RequestVideo(
										getContext().getSelf().narrow(), uri.path().substring(1)));
							} else if (uri.path().startsWith("/")) {
								String input = uri.path().substring(1);
								getContext().getLog().info("HTTP Video Request {}", input);

								if (uri.query().get("v").isPresent()) {
									input += "?v=" + uri.query().get("v").get();
								}

								String url;
								String videoCode;

								Matcher matcher = Regex.YOUTUBE_LINK.matcher(input);

								if (matcher.find()) {
									videoCode = matcher.group("link");
									url = "https://youtube.com/watch?v=" + videoCode;

									Log.v(TAG, String.format("Input %s - %s (link) > %s (code)", uri, url, videoCode));
								} else if ((matcher = Regex.VIDEO_CODE.matcher(input)).find()) {
									videoCode = matcher.group();
									url = "https://youtube.com/watch?v=" + videoCode;

									Log.v(TAG, String.format("Input %s - %s (link) > %s (code)", uri, url, videoCode));
								} else {
									return INVALID_LINK;
								}

								CacheManager.RequestVideo requestVideo =
										new CacheManager.RequestVideo(getContext().getSelf().narrow(), videoCode);

								return getHttpResponse(requestVideo);
							}
						}
						return NOT_FOUND;
					}

					private HttpResponse getHttpResponse(CacheManager.RequestVideo req) {
						context.getSelf().tell(req);

						long requestTime = System.currentTimeMillis();
						long currentTime = System.currentTimeMillis();
						while (!responses.containsKey(req.getRequestId()) && 10000 > currentTime - requestTime) {
							currentTime = System.currentTimeMillis();
							try {
								Thread.sleep(500);
							} catch (InterruptedException ignore) {
							}
						}

						UnicodeVideo video = responses.getOrDefault(req.getRequestId(), null);

						if (video != null) {
							int FPS = 24;
							int frameLength = video.getFrames().size();
							long duration = frameLength < 24 ? 1 : frameLength / FPS;

							Source<ByteString, NotUsed> source = Source.range(0, frameLength - 1)
									.map(str -> ByteString.fromString(video.getFrames().get(str) + "\n"))
									.throttle(frameLength, Duration.ofSeconds(duration * 4));

							return HttpResponse.create()
									.withEntity(HttpEntities.createChunked(ContentTypes.TEXT_PLAIN_UTF8, source));
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

	public static class RegisterRequest implements Command {
		private final String sender;
		private final String location;

		public RegisterRequest(String sender, String location) {
			this.sender = sender;
			this.location = location;
		}
	}
}
