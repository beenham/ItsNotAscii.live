package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import live.itsnotascii.core.Event;
import lombok.Getter;

import java.time.Duration;
import java.util.*;

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
                        new CachesUpdated(listing.getServiceInstances(Cache.CACHE_SERVICE_KEY)));
        context.getSystem().receptionist().tell(Receptionist.subscribe(Cache.CACHE_SERVICE_KEY, subscriptionAdapter));

        context.getLog().info("I am alive! {}", context.getSelf());
    }

    public static Behavior<Command> create(String id) {
        return Behaviors.setup(c -> new CacheManager(c, id));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(CachesUpdated.class, this::onCachesUpdated)
                .onMessage(Test.class, this::onReceiveTest)
                .onMessage(RequestVideo.class, this::onRequestVideo)
                .build();
    }

    private CacheManager onReceiveTest(Test r) {
        getContext().getLog().info("This is the message received: {}", r.message);
        caches.values().forEach(c -> c.tell(r));
        return this;
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

    public interface Command extends Event {
    }

    private static final class CachesUpdated implements Command {
        private final Set<ActorRef<Cache.Command>> newCaches;

        public CachesUpdated(Set<ActorRef<Cache.Command>> caches) {
            this.newCaches = caches;
        }
    }

    public static class Test implements Cache.Command, Command {
        @Getter
        private String message;

        public Test(String message) {
            this.message = message;
        }
    }

    public static final class RequestVideo implements CacheQuery.Command, Command {
        @Getter
        final long requestId;
        private static long uniqueRequestId = 0;
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
        final VideoFile videoFile;

        public RespondVideo(long requestId, VideoFile videoFile) {
            this.requestId = requestId;
            this.videoFile = videoFile;
        }
    }

    public interface VideoFile extends CacheManager.Command {
    }

    public static final class Video implements VideoFile {
        @Getter
        final String value;

        public Video(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            return this.value.equals(((Video) o).value);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < value.length(); i++) {
                hash = hash * 31 + value.charAt(i);
            }
            return hash;
        }

        @Override
        public String toString() {
            return value + "\n";
        }
    }

    public enum VideoNotFound implements VideoFile {
        INSTANCE
    }

//	public enum CacheNotAvailable implements VideoFile {
//		INSTANCE
//	}

    public enum CacheTimedOut implements VideoFile {
        INSTANCE
    }
}
