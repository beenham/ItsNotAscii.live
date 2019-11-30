package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import live.itsnotascii.CacheMain;
import live.itsnotascii.core.UnicodeVideo;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Cache extends AbstractBehavior<Cache.Command> {
	public static ServiceKey<Command> CACHE_SERVICE_KEY = ServiceKey.create(Command.class, "Cache");

	private final String id;

	private Map<String, UnicodeVideo> cachedVideos;

	private Cache(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		cachedVideos = new HashMap<>();

		List<byte[]> byteList = Arrays.asList("This", "is", "a", "test").stream().map(s -> s.getBytes()).collect(Collectors.toList());;
		UnicodeVideo unicodeVideo = new UnicodeVideo("test", byteList);
		cachedVideos.put("test", unicodeVideo);

		context.getLog().info("I am alive! {}", context.getSelf());
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(context -> {
			context.getSystem().receptionist()
					.tell(Receptionist.register(CACHE_SERVICE_KEY, context.getSelf().narrow()));
			return new Cache(context, id);
		});
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(CacheManager.Test.class, test -> {
					System.out.println("Message received from manager: " + test.getMessage());
					return Behaviors.same();
				})
				.onMessage(RetrieveVideo.class, this::onRetrieveVideo)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	// #respond-reply
	private Behavior<Command> onRetrieveVideo(RetrieveVideo r) {
		getContext().getLog().info("Retrieving video! {}", r.videoCode);
		CacheManager.WrappedVideo wrappedVideo = new CacheManager.WrappedVideo(findVideo(r.videoCode));
		r.replyTo.tell(new RespondVideo(r.requestId, id, wrappedVideo));
		return this;
	}

	private Behavior<Command> onPostStop() {
		getContext().getLog().info("Cache actor {} stopped", this);
		return Behaviors.stopped();
	}

	public interface Command extends live.itsnotascii.core.messages.Command, Serializable {
	}

	public static final class RetrieveVideo implements Command {
		final long requestId;
		final ActorRef<RespondVideo> replyTo;
		final String videoCode;

		public RetrieveVideo(long requestId, ActorRef<RespondVideo> replyTo, String videoCode) {
			this.requestId = requestId;
			this.replyTo = replyTo;
			this.videoCode = videoCode;
		}
	}

	public static final class RespondVideo implements Serializable {
		final long requestId;
		final String cacheId;
		final CacheManager.WrappedVideo video;

		public RespondVideo(long requestId, String cacheId, CacheManager.WrappedVideo video) {
			this.requestId = requestId;
			this.cacheId = cacheId;
			this.video = video;
		}
	}

	private UnicodeVideo findVideo(String name) {
		System.out.println("Fetching video " + name);
		if (cachedVideos.containsKey(name))
			return cachedVideos.get(name);

//		UnicodeVideo video = null;
		Path dir = null;

		try {
			dir = Paths.get(CacheMain.class.getResource("../../video/" + name + ".txt").toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		if (dir == null)
			dir = Paths.get("/videos/" + name + ".txt");

		try {
			assert dir.toFile().exists();
			InputStream in = Files.newInputStream(dir);
			int len = 0;
			int x;

			List<byte[]> frames = new ArrayList<>();

			while ((x = in.read()) > 0) {
				len += x - '0';
				while ((x = in.read()) != '\n') {
					len *= 10;
					len += x - '0';
				}
				byte[] buffer = new byte[len];
				in.read(buffer);
				frames.add(buffer);
				// empty start of line
				in.read();
				System.out.println("Length: " + len + ", " + new String(buffer).length());
				len = 0;
			}


			cachedVideos.put(name, new UnicodeVideo(name, frames));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return cachedVideos.getOrDefault(name, null);
	}
}
