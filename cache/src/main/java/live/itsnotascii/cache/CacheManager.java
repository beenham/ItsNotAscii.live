package live.itsnotascii.cache;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import live.itsnotascii.core.Event;

public class CacheManager extends AbstractBehavior<Event> {
	private final String id;

	private CacheManager(ActorContext<Event> context, String id) {
		super(context);
		this.id = id;
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

	public static class Test implements Event {
		private String message;
		public Test(String message) {
			this.message = message;
		}
	}
}
