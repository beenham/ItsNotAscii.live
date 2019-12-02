package live.itsnotascii.videoprocessor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.util.Log;
import lombok.Getter;

import java.io.Serializable;

public class VideoProcessor extends AbstractBehavior<VideoProcessor.Command> {
	private static final String TAG = VideoProcessor.class.getCanonicalName();
	public static ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "VideoProcessor");

	private VideoProcessor(ActorContext<Command> context) {
		super(context);

		Log.i(TAG, String.format("I'm alive! %s", context.getSelf()));
		context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf().narrow()));
	}

	public static Behavior<Command> create() {
		return Behaviors.setup(VideoProcessor::new);
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(GetVideo.class, this::onRequest)
				.build();
	}

	private VideoProcessor onRequest(GetVideo r) {
		Log.v(TAG, String.format("Request #%s for video %s (%s)", r.request.getId(), r.request.getUrl(), r.request.getCode()));
		r.replyTo.tell(new RespondVideo(r.request, null));
		// TODO
		return this;
	}

	public interface Command extends live.itsnotascii.core.messages.Command {}

	static final class GetVideo implements Command, Serializable {
		private final VideoProcessorManager.Request request;
		private final ActorRef<RespondVideo> replyTo;

		public GetVideo(VideoProcessorManager.Request request, ActorRef<RespondVideo> replyTo) {
			this.request = request;
			this.replyTo = replyTo;
		}
	}

	public static final class RespondVideo implements Serializable {
		@Getter
		private final VideoProcessorManager.Request request;
		@Getter
		private final UnicodeVideo video;

		public RespondVideo(VideoProcessorManager.Request request, UnicodeVideo video) {
			this.request = request;
			this.video = video;
		}
	}
}
