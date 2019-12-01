package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import live.itsnotascii.core.UnicodeVideo;
import lombok.Getter;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CacheManager extends AbstractBehavior<CacheManager.Command> {
	private final String id;
	private final Map<String, ActorRef<Cache.Command>> caches;

	private CacheManager(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		this.caches = new HashMap<>();

		// Create listener for when a Cache component is created
		ActorRef<Receptionist.Listing> subscriptionAdapter =
				context.messageAdapter(Receptionist.Listing.class, listing ->
						new CachesUpdated(listing.getServiceInstances(Cache.SERVICE_KEY)));
		context.getSystem().receptionist().tell(Receptionist.subscribe(Cache.SERVICE_KEY, subscriptionAdapter));

		context.getLog().info("I am alive! {}", context.getSelf());
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(c -> new CacheManager(c, id));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(CachesUpdated.class, this::onCachesUpdated)
				.onMessage(RequestVideo.class, this::onRequestVideo)
				.build();
	}

	private CacheManager onCachesUpdated(CachesUpdated event) {
		caches.clear();
		event.newCaches.forEach(c -> caches.put(c.path().name(), c));
		getContext().getLog().info("List of services registered with the receptionist changed: {}", event.newCaches);
		return this;
	}

	private CacheManager onRequestVideo(RequestVideo request) {
		if (caches.size() == 0) {
			request.replyTo.tell(new RespondVideo(request.requestId, null));
		}

		Map<String, ActorRef<Cache.Command>> cacheIdToActorCopy = new HashMap<>(this.caches);

		getContext()
				.spawnAnonymous(
						CacheQuery.create(
								cacheIdToActorCopy, request.requestId, request.replyTo, request.videoCode, Duration.ofSeconds(3)));
		return this;
	}

	public enum VideoNotFound implements Video {
		INSTANCE
	}

	public enum CacheTimedOut implements Video {
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

	public static final class RequestVideo implements CacheQuery.Command, Command {
		private static long uniqueRequestId = 0;
		@Getter
		final long requestId;
		final ActorRef<RespondVideo> replyTo;
		@Getter
		final String videoCode;

		public RequestVideo(ActorRef<RespondVideo> replyTo, String videoCode) { //, String groupId) {
			this.requestId = uniqueRequestId++;
			this.replyTo = replyTo;
			this.videoCode = videoCode;
		}
	}

	public static final class RespondVideo implements Command {
		@Getter
		final long requestId;
		@Getter
		final Video video;

		public RespondVideo(long requestId, Video video) {
			this.requestId = requestId;
			this.video = video;
		}
	}
}
