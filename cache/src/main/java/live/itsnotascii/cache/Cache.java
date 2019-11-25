package live.itsnotascii.cache;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import live.itsnotascii.core.Event;

import java.io.Serializable;

public class Cache extends AbstractBehavior<Cache.Command> {
	public static final String REGISTER_REQUEST = "CacheRegisterRequest";
	public static final String REGISTER_ACCEPT = "CacheRegisterAccept";

	public static ServiceKey<Command> CACHE_SERVICE_KEY = ServiceKey.create(Command.class, "Cache");

	private final String id;

	private Cache(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;

		context.getLog().info("I am alive! {}", context.getSelf());
	}

	public static Behavior<Command> create(String id) {
		return Behaviors.setup(context -> {
			context.getSystem().receptionist()
					.tell(Receptionist.register(CACHE_SERVICE_KEY, context.getSelf().narrow()));
			return new Cache(context, id);
		});
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(CacheManager.Test.class, test -> {
					System.out.println("Message received from manager: " + test.getMessage());
					return Behaviors.same();
				})
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private Behavior<Command> onPostStop() {
		getContext().getLog().info("Cache actor {} stopped", this);
		return Behaviors.stopped();
	}

	public interface Command extends Event, Serializable {}
}
