package live.itsnotascii.main;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Event;

public class Listener extends AbstractBehavior<Event> {
	private final String id;
	private ActorRef<CacheManager.Command> cacheManager;

	private Listener(ActorContext<Event> context, String id) {
		super(context);
		this.id = id;
		context.getLog().info("I am alive! {}", context.getSelf());

		this.cacheManager = context.spawn(CacheManager.create("CacheManager"), "CacheManager");
	}

	public static Behavior<Event> create(String id) {
		return Behaviors.setup(c -> new Listener(c, id));
	}

	@Override
	public Receive<Event> createReceive() {
		return newReceiveBuilder()
				.onMessage(RegisterRequest.class, this::onRegisterRequest)
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

	private Listener onRegisterRequest(RegisterRequest r) {
		System.out.println(getContext().getSystem().name());
		System.out.println(getContext().getSystem().path());

		Http http = Http.get(getContext().getSystem().classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(r.sender)
				.addHeader(HttpHeader.parse(Cache.REGISTER_ACCEPT, r.location));
		http.singleRequest(request);

		System.out.println("Sending register request: " + r.location + " to " + r.sender);
		return this;
	}

	public static class RegisterRequest implements Event {
		private final String sender;
		private final String location;

		public RegisterRequest(String sender, String location) {
			this.sender = sender;
			this.location = location;
		}
	}
}
