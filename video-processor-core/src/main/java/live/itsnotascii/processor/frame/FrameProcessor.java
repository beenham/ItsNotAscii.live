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
import java.awt.image.DataBufferByte;
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

		/*f.frames.keySet().forEach(k -> {
			f.replyTo.tell(new VideoProcessor.UnicodeFrame(f.videoCode, k, Constants.CLEAR_SCREEN + k.toString()));
		});*/

		for (Map.Entry<Integer, BufferedImage> frame : f.frames.entrySet()) {
			int index = frame.getKey();
			BufferedImage image = frame.getValue();

			StringBuilder builder = new StringBuilder();

			int width = image.getWidth();
			int height = image.getHeight();

			byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

			Log.wtf(TAG, "Frame " + index);

			for (int y = 0; y < height; y += 8) {
				for (int x = 0; x < width; x += 4) {
					int i = (y * width + x) * 3;
					byte r = pixels[i];
					byte g = pixels[i + 1];
					byte b = pixels[i + 2];

					Colors.Color color = Colors.getColor(r, g, b, Colors.ColorProfile.COLOR_PROFILE_8BIT);
					color = Colors.mapToColorProfile(color, Colors.ColorProfile.COLOR_PROFILE_8BIT);

					builder.append("\033[");
					builder.append(Colors.ansiCode(color, Colors.ColorProfile.COLOR_PROFILE_8BIT, true));
					builder.append("m█");
				}

				builder.append("\033[0m\n");

			}
			f.replyTo.tell(new VideoProcessor.UnicodeFrame(f.videoCode, index, Constants.CLEAR_SCREEN + builder.toString()));
		}

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
