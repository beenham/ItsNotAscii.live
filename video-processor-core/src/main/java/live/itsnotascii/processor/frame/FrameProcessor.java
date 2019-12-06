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

import java.awt.*;
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

	public static BufferedImage resize(BufferedImage img, int newW, int newH) {
		Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
		BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_3BYTE_BGR);

		Graphics2D g2d = dimg.createGraphics();
		g2d.drawImage(tmp, 0, 0, null);
		g2d.dispose();

		return dimg;
	}

	private FrameProcessor onProcessFrames(ProcessFrames f) {
//		Log.v(TAG, String.format("%s Frames received for %s", f.frames.size(), f.videoCode));

		Colors.ColorProfile profile = Colors.ColorProfile.COLOR_PROFILE_24BIT;

		for (Map.Entry<Integer, BufferedImage> frame : f.frames.entrySet()) {
			int index = frame.getKey();
			BufferedImage image = frame.getValue();

			image = resize(image, 80, 45);

			StringBuilder builder = new StringBuilder();

			if (frame.getKey() == 0)
				builder.append(Constants.CLEAR_SCREEN);

			int width = image.getWidth();
			int height = image.getHeight();

			byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

			Log.wtf(TAG, "Frame " + index);

			for (int y = 0; y < height; y += 2) {
				Colors.Color previousFg = null, previousBg = null;
				for (int x = 0; x < width; x++) {
					Colors.Color fg, bg = null;
					{
						int i = (y * width + x) * 3;
						byte b = pixels[i];
						byte g = pixels[i + 1];
						byte r = pixels[i + 2];

						Colors.Color color = Colors.getColor(r, g, b, profile);
						fg = Colors.mapToColorProfile(color, profile);
					}
					{
						if (y + 1 < height) {
							int i = ((y + 1) * width + x) * 3;
							byte b = pixels[i];
							byte g = pixels[i + 1];
							byte r = pixels[i + 2];

							Colors.Color color = Colors.getColor(r, g, b, profile);
							bg = Colors.mapToColorProfile(color, profile);
						}
					}


					if (fg != previousFg || bg != previousBg) {
						builder.append("\033[");
						builder.append(Colors.ansiCode(fg, profile, true));
						if (bg != null && bg != previousBg) {
							builder.append(";");
							builder.append(Colors.ansiCode(bg, profile, false));
						}
						builder.append("m");
					}
					builder.append("▀");

					previousFg = fg;
					previousBg = bg;
				}

				builder.append("\033[0m\n");

			}
			f.replyTo.tell(new VideoProcessor.UnicodeFrame(f.videoCode, index, "\033[0;0H" + builder.toString()));
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
