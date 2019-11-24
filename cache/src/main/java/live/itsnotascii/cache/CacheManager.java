package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import live.itsnotascii.core.Event;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class CacheManager extends AbstractBehavior<Event> {
	private final String id;
	private final Set<ActorRef<Event>> caches;

	private CacheManager(ActorContext<Event> context, String id) {
		super(context);
		this.id = id;
		this.caches = new HashSet<>();
	}

	public static Behavior<Event> create(String id) {
		return Behaviors.setup(c -> new CacheManager(c, id));
	}

	@Override
	public Receive<Event> createReceive() {
		return newReceiveBuilder()
				.onMessage(Test.class, this::onReceiveTest)
				.build();
	}

	private CacheManager onReceiveTest(Test r) {
		getContext().getLog().info("This is the message received: {}", r.message);
		return this;
	}

	public static class Init implements Event {
		@Getter private final String id;

		public Init(String id) {
			this.id = id;
		}
	}

	public static class Test implements Event {
		private String message;

		public Test(String message) {
			this.message = message;
		}
	}
}
