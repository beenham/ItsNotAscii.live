package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.time.Duration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CacheQuery extends AbstractBehavior<CacheQuery.Command> {

    private final long requestId;
    private final ActorRef<CacheManager.RespondVideo> requester;
    private final Set<String> stillWaiting;

    private CacheQuery(
            Map<String, ActorRef<Cache.Command>> cacheIdToActor,
            long requestId,
            ActorRef<CacheManager.RespondVideo> requester,
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
            context.watchWith(entry.getValue(), new CacheTerminated(entry.getKey()));
            entry.getValue().tell(new Cache.RetrieveVideo(0L, respondTemperatureAdapter, videoCode));
        }
        stillWaiting = new HashSet<>(cacheIdToActor.keySet());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(WrappedRespondVideo.class, this::onRespondVideo)
                .onMessage(CacheTerminated.class, this::onCacheTerminated)
                .onMessage(CollectionTimeout.class, this::onCollectionTimeout)
                .build();
    }

    private Behavior<Command> onRespondVideo(WrappedRespondVideo r) {
        stillWaiting.remove(r.response.cacheId);
        CacheManager.Video video = new CacheManager.Video(r.response.video);

        getContext().getSystem().log().info("Video Response Cache Query");

        if (video.value != null) {
            requester.tell(new CacheManager.RespondVideo(requestId, video));
            getContext().getSystem().log().debug("Cache Query Found Video for request ID {}", r.response.requestId);
        } else if (stillWaiting.isEmpty()) {
            requester.tell(new CacheManager.RespondVideo(requestId, CacheManager.VideoNotFound.INSTANCE));
            getContext().getSystem().log().info("stillWaiting is empty for request ID {}", r.response.requestId);
        }

        return this;
    }

    private Behavior<Command> onCacheTerminated(CacheTerminated terminated) {
        if (stillWaiting.contains(terminated.cacheId)) {
            stillWaiting.remove(terminated.cacheId);
        }
        getContext().getSystem().log().info("Cache terminated");

        if (stillWaiting.isEmpty()) {
            requester.tell(new CacheManager.RespondVideo(requestId, CacheManager.VideoNotFound.INSTANCE));
        }
        return this;
    }

    private Behavior<Command> onCollectionTimeout(CollectionTimeout timeout) {
        getContext().getSystem().log().info("Timeout");
        requester.tell(new CacheManager.RespondVideo(requestId, CacheManager.CacheTimedOut.INSTANCE));
        return this;
    }

    public interface Command {
    }

    private enum CollectionTimeout implements Command {
        INSTANCE
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

    public static Behavior<Command> create(
            Map<String, ActorRef<Cache.Command>> cacheIdToActor,
            long requestId,
            ActorRef<CacheManager.RespondVideo> requester,
            String videoCode,
            Duration timeout) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new CacheQuery(cacheIdToActor, requestId, requester, videoCode, timeout, context, timers)
                )
        );
    }
}
