package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import live.itsnotascii.util.Log;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CacheQuery extends AbstractBehavior<CacheQuery.Command> {
	private static final String TAG = CacheQuery.class.getCanonicalName();

	private final long requestId;
	private final ActorRef<CacheManager.Response> requester;
	private final Set<String> stillWaiting;
	private CacheManager.Response validResponse;

	private CacheQuery(
			Map<String, ActorRef<Cache.Command>> cacheIdToActor,
			long requestId,
			ActorRef<CacheManager.Response> requester,
			String videoCode,
			Duration timeout,
			ActorContext<Command> context,
			TimerScheduler<Command> timers) {

		super(context);
		this.requestId = requestId;
		this.requester = requester;

		timers.startSingleTimer(CollectionTimeout.class, CollectionTimeout.INSTANCE, timeout);

		ActorRef<Cache.RespondVideo> respondTemperatureAdapter =
				context.messageAdapter(Cache.RespondVideo.class, WrappedRespondVideo::new);

		for (Map.Entry<String, ActorRef<Cache.Command>> entry : cacheIdToActor.entrySet()) {
			Log.v(TAG, String.format("Sending request to %s", entry.getValue()));
			context.watchWith(entry.getValue(), new CacheTerminated(entry.getKey()));
			entry.getValue().tell(new Cache.RetrieveVideo(0L, respondTemperatureAdapter, videoCode));
		}
		stillWaiting = new HashSet<>(cacheIdToActor.keySet());
	}

	public static Behavior<Command> create(
			Map<String, ActorRef<Cache.Command>> cacheIdToActor,
			long requestId,
			ActorRef<CacheManager.Response> requester,
			String videoCode,
			Duration timeout) {
		return Behaviors.setup(context ->
				Behaviors.withTimers(timers ->
						new CacheQuery(cacheIdToActor, requestId, requester, videoCode, timeout, context, timers)
				)
		);
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(WrappedRespondVideo.class, this::onRespondVideo)
				.onMessage(CacheTerminated.class, this::onCacheTerminated)
				.onMessage(CollectionTimeout.class, this::onCollectionTimeout)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Behavior<Command> onRespondVideo(WrappedRespondVideo r) {
		stillWaiting.remove(r.response.cacheId);
		CacheManager.WrappedVideo wrappedVideo = r.response.video;

		Log.v(TAG, String.format("%s: Video response for #%s from cache %s", getContext().getSelf(),
				r.response.requestId, r.response.cacheId));
		if (wrappedVideo.video != null && wrappedVideo.video.getFrames() != null) {
			this.validResponse = new CacheManager.Response(requestId, wrappedVideo);
			Log.v(TAG, String.format("%s: Cache %s Found Video for #%s", getContext().getSelf(),
					r.response.cacheId, r.response.requestId));
			stillWaiting.clear();
		}

		return stopWhenFinished();
	}

	private Behavior<Command> onCacheTerminated(CacheTerminated terminated) {
		stillWaiting.remove(terminated.cacheId);
		return stopWhenFinished();
	}

	private Behavior<Command> onCollectionTimeout(CollectionTimeout timeout) {
		Log.v(TAG, String.format("%s: Timeout Reached.", getContext().getSelf()));
		stillWaiting.clear();
		return stopWhenFinished();
	}

	private Behavior<Command> stopWhenFinished() {
		if (stillWaiting.isEmpty()) {
			requester.tell(Objects.requireNonNullElseGet(validResponse,
					() -> new CacheManager.Response(requestId, CacheManager.VideoNotFound.INSTANCE)));
			return Behaviors.stopped();
		}
		return this;
	}

	private Behavior<Command> onPostStop() {
		Log.v(TAG, String.format("Killing query %s", this.getContext().getSelf()));
		return this;
	}

	private enum CollectionTimeout implements Command {
		INSTANCE
	}

	public interface Command {
	}

	static class WrappedRespondVideo implements Command {
		final Cache.RespondVideo response;

		WrappedRespondVideo(Cache.RespondVideo response) {
			this.response = response;
		}
	}

	private static class CacheTerminated implements Command {
		final String cacheId;

		private CacheTerminated(String cacheId) {
			this.cacheId = cacheId;
		}
	}
}
