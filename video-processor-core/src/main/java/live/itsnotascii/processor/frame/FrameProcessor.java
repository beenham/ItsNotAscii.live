package live.itsnotascii.processor.frame;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import live.itsnotascii.core.Constants;
import live.itsnotascii.processor.video.VideoProcessor;
import live.itsnotascii.util.Log;

import java.awt.image.BufferedImage;
import java.util.Map;

public class FrameProcessor extends AbstractBehavior<FrameProcessor.Command> {
	private static final String TAG = FrameProcessor.class.getCanonicalName();

	private FrameProcessor(ActorContext<Command> context) {
		super(context);
		Log.v(TAG, String.format("I'm alive! %s", getContext().getSelf()));
	}

	public static Behavior<Command> create() {
		return Behaviors.setup(FrameProcessor::new);
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(ProcessFrames.class, this::onProcessFrames)
				.onSignal(PostStop.class, s -> onPostStop())
				.build();
	}

	private FrameProcessor onProcessFrames(ProcessFrames f) {
		Log.v(TAG, String.format("%s Frames received for %s", f.frames.size(), f.videoCode));
		f.frames.keySet().forEach(k -> {
			f.replyTo.tell(new VideoProcessor.UnicodeFrame(f.videoCode, k, Constants.CLEAR_SCREEN + k.toString()));
		});
		// TODO

		return this;
	}

	private Behavior<Command> onPostStop() {
		Log.v(TAG, String.format("Frame Processor actor %s stopped", getContext().getSelf()));
		return Behaviors.stopped();
	}

	public interface Command extends live.itsnotascii.core.messages.Command {
	}

	public static class ProcessFrames implements Command {
		private final Map<Integer, BufferedImage> frames;
		private final String videoCode;
		private final ActorRef<VideoProcessor.Command> replyTo;

		public ProcessFrames(Map<Integer, BufferedImage> frames, String videoCode, ActorRef<VideoProcessor.Command> replyTo) {
			this.frames = frames;
			this.videoCode = videoCode;
			this.replyTo = replyTo;
		}
	}
}
