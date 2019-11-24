package live.itsnotascii.cache;

import akka.actor.AddressFromURIString;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.JoinSeedNodes;
import live.itsnotascii.core.Event;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;

public class Cache extends AbstractBehavior<Event> {
	public static final String REGISTER_REQUEST = "CacheRegisterRequest";
	public static final String REGISTER_ACCEPT = "CacheRegisterAccept";

	public static ServiceKey<Command> CACHE_SERVICE_KEY = ServiceKey.create(Command.class, "Cache");

	private final String id;

	private Cache(ActorContext<Event> context, String id) {
		super(context);
		this.id = id;

		getContext().getLog().info("I am alive! {}", getContext().getSelf());
	}

	public static Behavior<Event> create(String id) {
		return Behaviors.setup(context -> {
			context.getSystem().receptionist().tell(Receptionist.register(CACHE_SERVICE_KEY, context.getSelf().narrow()));

			return new Cache(context, id);
		});
	}

	@Override
	public Receive<Event> createReceive() {
		return newReceiveBuilder()
				.onMessage(CacheManager.Test.class, test -> {System.out.println(test); return Behaviors.same();})
				.build();
	}

	public interface Command extends Event, Serializable {

	}

	public static class Init implements Event {
		@Getter
		private final String id, location;

		public Init(String id, String location) {
			this.id = id;
			this.location = location;
		}
	}
}
