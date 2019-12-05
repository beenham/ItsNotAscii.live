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
import live.itsnotascii.processor.video.VideoProcessorManager;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;

public class Coordinator extends AbstractBehavior<Command> {
	private static final String TAG = Coordinator.class.getCanonicalName();

	private final String id;
	private final ActorRef<CacheManager.Command> cacheManager;
	private final ActorRef<VideoProcessorManager.Command> videoProcessorManager;

	private final Map<Long, VideoRequest> pendingRequests;
	private final Map<Long, UnicodeVideo> responses;

	private Coordinator(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		this.pendingRequests = new HashMap<>();
		this.responses = new HashMap<>();
		Log.i(TAG, String.format("Listener (%s) started", getContext().getSelf()));

		this.cacheManager = context.spawn(CacheManager.create("CacheManager"), "CacheManager");
		this.videoProcessorManager = context.spawn(VideoProcessorManager.create(), "VideoProcessorManager");

		setupHttpListener();
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(c -> new Coordinator(c, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(RegisterRequest.class, this::onRegisterRequest)
				.onMessage(VideoRequest.class, this::onVideoRequest)
				.onMessage(CacheManager.Response.class, this::onCacheResponse)
				.onMessage(VideoProcessorManager.Response.class, this::onVideoResponse)
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

	private Coordinator onVideoRequest(VideoRequest r) {
		this.pendingRequests.put(r.id, r);
		this.cacheManager.tell(new CacheManager.Request(getContext().getSelf().narrow(), r.id, r.code));
		return this;
	}

	private Coordinator onCacheResponse(CacheManager.Response r) {
		if (r.getVideo() instanceof CacheManager.WrappedVideo) {
			CacheManager.WrappedVideo video = (CacheManager.WrappedVideo) r.getVideo();
			pendingRequests.remove(r.getId());
			responses.put(r.getId(), video.getVideo());
			Log.v(TAG, String.format("Received response #%s from CacheManager", r.getId()));
		} else if (r.getVideo() instanceof CacheManager.VideoNotFound) {
			VideoRequest request = pendingRequests.get(r.getId());
			Log.v(TAG, String.format("Received response #%s from CacheManager (NOT FOUND)", r.getId()));
			this.videoProcessorManager.tell(new VideoProcessorManager.Request(getContext().getSelf().narrow(),
					request.id, request.url, request.code));
		}
		return this;
	}

	private Coordinator onVideoResponse(VideoProcessorManager.Response r) {
		pendingRequests.remove(r.getId());
		responses.put(r.getId(), r.getVideo());
		if (r.getVideo() != null) {
			cacheManager.tell(new Cache.StoreVideo(r.getVideo()));
		}
		Log.v(TAG, String.format("Received response #%s (%s) from VideoProcessorManager",
				r.getId(), r.getVideo() != null ? r.getVideo().getName() : null));
		return this;
	}

	private Coordinator onPostStop() {
		Log.i(TAG, String.format("Listener %s stopped.", this.id));
		return this;
	}

	private void setupHttpListener() {
		Arguments args = Arguments.get();
		ActorContext<Command> context = getContext();
		// Setting up HTTP listener
		final Materializer materializer = Materializer.createMaterializer(context.getSystem());
		final Function<HttpRequest, HttpResponse> requestHandler =
				new Function<>() {
					private final HttpResponse NOT_FOUND = HttpResponse.create()
							.withStatus(404)
							.withEntity(Constants.CLEAR_SCREEN + HttpResponses.NOT_FOUND);

					private final HttpResponse INVALID_LINK = HttpResponse.create()
							.withStatus(404)
							.withEntity(Constants.CLEAR_SCREEN + HttpResponses.INVALID_URL);

					private final HttpResponse WELCOME = HttpResponse.create()
							.withStatus(404)
							.withEntity(Constants.CLEAR_SCREEN + HttpResponses.WELCOME_SCREEN);

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
								String videoCode = uri.path().substring(1);
								return getHttpResponse(
										new VideoRequest(String.format("internal/%s", videoCode), videoCode));
							} else if (uri.path().startsWith("/")) {
								String input = uri.path().substring(1);

								if (uri.query().get("v").isPresent()) {
									input += "?v=" + uri.query().get("v").get();
								}

								String videoUrl, videoCode;
								Matcher matcher = Regex.YOUTUBE_LINK.matcher(input);

								if (matcher.find()) {
									videoCode = matcher.group("link");
									videoUrl = "https://youtube.com/watch?v=" + videoCode;
								} else if ((matcher = Regex.VIDEO_CODE.matcher(input)).find()) {
									videoCode = matcher.group();
									videoUrl = "https://youtube.com/watch?v=" + videoCode;
								} else {
									return INVALID_LINK;
								}

								Log.v(TAG, String.format("Request: %s (link) > %s (code)", videoUrl, videoCode));
								return getHttpResponse(new VideoRequest(videoUrl, videoCode));
							}
						}
						return NOT_FOUND;
					}

					private HttpResponse getHttpResponse(VideoRequest req) {
						context.getSelf().tell(req);
						while (!responses.containsKey(req.id)) {
							try {
								Thread.sleep(500);
							} catch (InterruptedException ignore) {
							}
						}

						UnicodeVideo video = responses.getOrDefault(req.id, null);

						if (video != null) {
							double FPS = video.getFrameRate();
							int frameLength = video.getFrames().size();
							long duration = frameLength < FPS ? 1 : Math.round(frameLength / FPS);

							Source<ByteString, NotUsed> source = Source.range(0, frameLength - 1)
									.map(str -> ByteString.fromString(video.getFrames().get(str) + "\n"))
									.throttle(frameLength, Duration.ofSeconds(duration));

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

	private static class VideoRequest implements Command {
		private static long uniqueRequestId = 0;
		private final long id;
		private final String url;
		private final String code;

		private VideoRequest(String url, String code) {
			this.id = uniqueRequestId++;
			this.url = url;
			this.code = code;
		}
	}
}
