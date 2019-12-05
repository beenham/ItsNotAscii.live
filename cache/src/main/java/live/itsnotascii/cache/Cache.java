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
		Log.i(TAG, String.format("I'm alive! (%s)", context.getSelf()));
		context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf().narrow()));
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(context -> new Cache(context, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(RetrieveVideo.class, this::onRetrieveVideo)
				.onMessage(StoreVideo.class, this::onStoreVideo)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	// #respond-reply
	private Cache onRetrieveVideo(RetrieveVideo r) {
		Log.i(TAG, String.format("Request received for video '%s'", r.videoCode));
		CacheManager.WrappedVideo wrappedVideo = new CacheManager.WrappedVideo(findVideo(r.videoCode));
		r.replyTo.tell(new RespondVideo(r.requestId, id, wrappedVideo));
		return this;
	}

	private Cache onStoreVideo(StoreVideo store) {
		Log.i(TAG, String.format("Request received to store video '%s'", store.video));
		storeVideo(store.video);
		return this;
	}

	private Behavior<Command> onPostStop() {
		Log.i(TAG, String.format("Cache actor %s stopped", getContext().getSelf()));
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
			if (Files.exists(Paths.get("./videos/" + name + ".txt")))
				dir = Paths.get("./videos/" + name + ".txt");
			else
				return null;

		try {
			assert dir.toFile().exists();
			InputStream in = Files.newInputStream(dir);
			int len = 0;
			int x;

			List<byte[]> frames = new ArrayList<>();
			x = in.read();
			if (x > 0) {
				len += x - '0';
				while ((x = in.read()) != '\n') {
					len *= 10;
					len += x - '0';
				}
			}
			int fps = len;
			len = 0;

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
				len = 0;
			}

			cachedVideos.put(name, new UnicodeVideo(name, frames, fps));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return cachedVideos.getOrDefault(name, null);
	}

	private void storeVideo(UnicodeVideo video) {
		cachedVideos.put(video.getName(), video);
		List<String> frames = video.getFrames();

		long fps = Math.round(video.getFrameRate());
		int totalByteSize = 0;
		totalByteSize += String.valueOf(fps).length() + 1;

		for (String frame : frames) {
			byte[] frameBytes = frame.getBytes();
			totalByteSize += String.valueOf(frameBytes.length).length() + 1 + frameBytes.length + 1;
		}

		byte[] outBytes = new byte[totalByteSize];
		int i = 0;
		for (byte b : String.valueOf(fps).getBytes())
			outBytes[i++] = b;
		outBytes[i++] = '\n';

		for (String frame : frames) {
			byte[] bytes = frame.getBytes();
			int size = bytes.length;
			for (byte b : String.valueOf(size).getBytes())
				outBytes[i++] = b;
			outBytes[i++] = '\n';
			for (byte b : bytes)
				outBytes[i++] = b;
			outBytes[i++] = '\n';
		}

		String fileName = String.format("./videos/%s.txt", video.getName());
		Log.i(TAG, String.format("Writing Unicode Video (%s) to file > %s", video, fileName));

		try {
			if (Files.notExists(Paths.get("./videos/")))
				Files.createDirectory(Paths.get("./videos/"));
			Files.write(Paths.get(fileName), outBytes);
		} catch (IOException e) {
			Log.e(TAG, String.format("Failed to file: %s", e.getMessage()));
		}
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

	public static final class StoreVideo implements CacheManager.Command, Command {
		final UnicodeVideo video;

		public StoreVideo(UnicodeVideo video) {
			this.video = video;
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
