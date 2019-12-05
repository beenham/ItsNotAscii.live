package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.util.Log;
import lombok.Getter;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CacheManager extends AbstractBehavior<CacheManager.Command> {
	private static final String TAG = CacheManager.class.getCanonicalName();

	private final String id;
	private final Map<String, ActorRef<Cache.Command>> caches;

	private CacheManager(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		this.caches = new HashMap<>();

		Log.i(TAG, String.format("I'm alive! (%s)", context.getSelf()));

		// Create listener for when a Cache component is created
		ActorRef<Receptionist.Listing> subscriptionAdapter =
				context.messageAdapter(Receptionist.Listing.class, listing ->
						new CachesUpdated(listing.getServiceInstances(Cache.SERVICE_KEY)));
		context.getSystem().receptionist().tell(Receptionist.subscribe(Cache.SERVICE_KEY, subscriptionAdapter));
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(c -> new CacheManager(c, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(CachesUpdated.class, this::onCachesUpdated)
				.onMessage(Request.class, this::onRequestVideo)
				.onMessage(Cache.StoreVideo.class, this::onStoreVideo)
				.build();
	}

	private CacheManager onCachesUpdated(CachesUpdated command) {
		caches.clear();
		if (command.newCaches.size() > 0)
			command.newCaches.forEach(c -> caches.put(c.path().toString(), c));

		Log.i(TAG, String.format("List of Caches Registered: %s (%s)", caches, caches.size()));
		return this;
	}

	private CacheManager onRequestVideo(Request request) {
		if (caches.size() == 0) {
			request.replyTo.tell(new Response(request.id, VideoNotFound.INSTANCE));
			return this;
		}

		Map<String, ActorRef<Cache.Command>> cacheIdToActorCopy = new HashMap<>(this.caches);

		getContext()
				.spawnAnonymous(
						CacheQuery.create(
								cacheIdToActorCopy, request.id, request.replyTo, request.videoCode, Duration.ofSeconds(3)));
		return this;
	}

	private CacheManager onStoreVideo(Cache.StoreVideo video) {
		caches.values().forEach(e -> e.tell(video));
		return this;
	}

	public enum VideoNotFound implements Video {
		INSTANCE
	}

	public interface Video extends Serializable {
	}

	public interface Command extends live.itsnotascii.core.messages.Command {
	}

	public static final class WrappedVideo implements Video {
		@Getter
		final UnicodeVideo video;

		public WrappedVideo(UnicodeVideo video) {
			this.video = video;
		}
	}

	private static final class CachesUpdated implements Command {
		private final Set<ActorRef<Cache.Command>> newCaches;

		public CachesUpdated(Set<ActorRef<Cache.Command>> caches) {
			this.newCaches = caches;
		}
	}

	public static final class Request implements Command {
		private final ActorRef<Response> replyTo;
		private final long id;
		private final String videoCode;

		public Request(ActorRef<Response> replyTo, long id, String videoCode) {
			this.id = id;
			this.replyTo = replyTo;
			this.videoCode = videoCode;
		}
	}

	public static final class Response implements Command {
		@Getter
		final long id;
		@Getter
		final Video video;

		public Response(long id, Video video) {
			this.id = id;
			this.video = video;
		}
	}
}
