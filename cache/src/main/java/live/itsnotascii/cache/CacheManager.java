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

import java.util.HashSet;
import java.util.Set;

public class CacheManager extends AbstractBehavior<CacheManager.Command> {
	private final String id;
	private final Set<ActorRef<Cache.Command>> caches;

	private CacheManager(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		this.caches = new HashSet<>();
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
				.build();
	}

	private CacheManager onReceiveTest(Test r) {
		getContext().getLog().info("This is the message received: {}", r.message);
		caches.forEach(c -> c.tell(r));
		return this;
	}

	private CacheManager onCachesUpdated(CachesUpdated event) {
		caches.clear();
		caches.addAll(event.newCaches);
		getContext().getLog().info("List of services registered with the receptionist changed: {}", event.newCaches);
		return this;
	}

	public interface Command extends Event {}

	private static final class CachesUpdated implements Command {
		public final Set<ActorRef<Cache.Command>> newCaches;

		public CachesUpdated(Set<ActorRef<Cache.Command>> caches) {
			this.newCaches = caches;
		}
	}

	public static class Test implements Cache.Command, Command {
		@Getter private String message;

		public Test(String message) {
			this.message = message;
		}
	}
}
