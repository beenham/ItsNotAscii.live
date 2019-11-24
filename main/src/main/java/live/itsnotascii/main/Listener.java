package live.itsnotascii.main;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Event;
import lombok.Getter;

public class Listener extends AbstractBehavior<Event> {
	private final String id;
	private ActorRef<Event> cacheManager;

	private Listener(ActorContext<Event> context, String id) {
		super(context);
		this.id = id;
		context.getLog().info("Listener {} started", this.id);
	}

	public static Behavior<Event> create(String id) {
		return Behaviors.setup(c -> new Listener(c, id));
	}

	@Override
	public Receive<Event> createReceive() {
		return newReceiveBuilder()
				.onMessage(InitCacheManager.class, this::onCreateCacheManager)
				.onMessage(CacheManager.Test.class, this::test)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Listener test(CacheManager.Test r) {
		this.cacheManager.tell(r);
		return this;
	}

	private Listener onPostStop() {
		getContext().getLog().info("Listener {} stopped.", this.id);
		return this;
	}

	public static final class InitCacheManager implements Event {
		@Getter private final String id;

		public InitCacheManager(String id) {
			this.id = id;
		}
	}

	private Listener onCreateCacheManager(InitCacheManager r) {
		ActorRef<Event> manager = getContext().getSystem()
				.systemActorOf(CacheManager.create(r.getId()), r.getId(), Props.empty());
		getContext().getLog().info("I am alive! {}", manager);
		this.cacheManager = manager;

		return this;
	}
}
