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
import live.itsnotascii.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cache extends AbstractBehavior<Cache.Command> {
	public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "Cache");
	public static final List<String> INTERNAL_VIDEOS = Arrays.asList("thetragedy");
	private static final String TAG = Cache.class.getCanonicalName();
	private final String id;
	private Map<String, UnicodeVideo> cachedVideos;

	private Cache(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		cachedVideos = new HashMap<>();

		context.getLog().info("I am alive! {}", context.getSelf());
		context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf().narrow()));
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(context -> new Cache(context, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(RetrieveVideo.class, this::onRetrieveVideo)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	// #respond-reply
	private Behavior<Command> onRetrieveVideo(RetrieveVideo r) {
		Log.i(TAG, String.format("Request received for video '%s'", r.videoCode));
		CacheManager.WrappedVideo wrappedVideo = new CacheManager.WrappedVideo(findVideo(r.videoCode));
		r.replyTo.tell(new RespondVideo(r.requestId, id, wrappedVideo));
		return this;
	}

	private Behavior<Command> onPostStop() {
		getContext().getLog().info("Cache actor {} stopped", this);
		return Behaviors.stopped();
	}

	private UnicodeVideo findVideo(String name) {
		Log.v(TAG, String.format("Fetching video '%s'", name));

		if (cachedVideos.containsKey(name))
			return cachedVideos.get(name);

		Path dir = null;

		if (INTERNAL_VIDEOS.contains(name.toLowerCase()))
			try {
				dir = Paths.get(CacheMain.class.getResource("../../video/" + name + ".txt").toURI());
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

		if (dir == null)
			if (Files.exists(Paths.get("/videos/" + name + ".txt")))
				dir = Paths.get("/videos/" + name + ".txt");
			else
				return null;

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
//				Log.v(TAG, String.format("Target length: %s | Actual length: %s", len, new String(buffer).length()));
				len = 0;
			}

			cachedVideos.put(name, new UnicodeVideo(name, frames));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return cachedVideos.getOrDefault(name, null);
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
}
