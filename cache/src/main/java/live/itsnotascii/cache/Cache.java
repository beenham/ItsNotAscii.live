package live.itsnotascii.cache;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Cache extends AbstractBehavior<Cache.Command> {
	public static ServiceKey<Command> CACHE_SERVICE_KEY = ServiceKey.create(Command.class, "Cache");

	private final String id;

	private Map<String, String> videos;

	private Cache(ActorContext<Command> context, String id) {
		super(context);
		this.id = id;
		videos = new HashMap<>();

		videos.put("test", "Test Video -> Successful");

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
				.onMessage(RetrieveVideo.class, this::onRetrieveVideo)
//				.onMessage(Passivate.class, m -> Behaviors.stopped())
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	// #respond-reply
	private Behavior<Command> onRetrieveVideo(RetrieveVideo r) {
		getContext().getLog().info("Retrieving video! {}", r.videoCode);
		r.replyTo.tell(new RespondVideo(r.requestId, id, videos.getOrDefault(r.videoCode, null)));
		return this;
	}

	private Behavior<Command> onPostStop() {
		getContext().getLog().info("Cache actor {} stopped", this);
		return Behaviors.stopped();
	}

	public interface Command extends live.itsnotascii.core.messages.Command, Serializable {
	}

	public static final class RetrieveVideo implements Command {
		final long requestId;
		final ActorRef<RespondVideo> replyTo;
		final String videoCode;

		public RetrieveVideo(long requestId, ActorRef<RespondVideo> replyTo, String videoCode) {
			this.requestId = requestId;
			this.replyTo = replyTo;
			this.videoCode = videoCode;
		}
	}

	public static final class RespondVideo implements Serializable {
		final long requestId;
		final String cacheId;
		final String video;

		public RespondVideo(long requestId, String cacheId, String video) {
			this.requestId = requestId;
			this.cacheId = cacheId;
			this.video = video;
		}
	}
}
