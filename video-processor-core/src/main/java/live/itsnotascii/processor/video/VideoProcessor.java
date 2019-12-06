package live.itsnotascii.processor.video;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.processor.frame.FrameProcessor;
import live.itsnotascii.util.Log;
import lombok.Getter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VideoProcessor extends AbstractBehavior<VideoProcessor.Command> {
	private static final String TAG = VideoProcessor.class.getCanonicalName();
	public static ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "VideoProcessor");

	protected final Map<String, List<ActorRef<FrameProcessor.Command>>> frameWorkers;
	protected final Map<String, GetVideo> requests;

	protected VideoProcessor(ActorContext<Command> context) {
		super(context);
		this.frameWorkers = new HashMap<>();
		this.requests = new HashMap<>();

		Log.i(TAG, String.format("I'm alive! %s", context.getSelf()));
		context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf().narrow()));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(GetVideo.class, this::onRequest)
				.onMessage(UnicodeFrame.class, this::onFrameReceived)
				.onMessage(Completed.class, this::onCompletion)
				.build();
	}

	protected abstract VideoProcessor onRequest(GetVideo r);

	private VideoProcessor onFrameReceived(UnicodeFrame frame) {
		String videoCode = frame.videoCode;
		requests.get(videoCode).replyTo.tell(frame);
		return this;
	}

	private VideoProcessor onCompletion(Completed completed) {
		frameWorkers.get(completed.videoCode).forEach(getContext()::stop);
		frameWorkers.remove(completed.videoCode);
		requests.remove(completed.videoCode);
		return this;
	}

	public interface Command extends live.itsnotascii.core.messages.Command {
	}

	static final class GetVideo implements Command, Serializable {
		protected final VideoProcessorManager.Request request;
		protected final ActorRef<VideoQuery.Command> replyTo;

		public GetVideo(VideoProcessorManager.Request request, ActorRef<VideoQuery.Command> replyTo) {
			this.request = request;
			this.replyTo = replyTo;
		}
	}

	public static final class RespondVideo implements VideoQuery.Command, Serializable {
		@Getter
		private final VideoProcessorManager.Request request;
		@Getter
		private final UnicodeVideo video;

		public RespondVideo(VideoProcessorManager.Request request, UnicodeVideo video) {
			this.request = request;
			this.video = video;
		}
	}

	public static final class Completed implements Command, Serializable {
		private final String videoCode;

		public Completed(String videoCode) {
			this.videoCode = videoCode;
		}
	}

	public static final class VideoInfo implements VideoQuery.Command, Serializable {
		protected final int frameCount;
		protected final double frameRate;

		public VideoInfo(int frameCount, double frameRate) {
			this.frameCount = frameCount;
			this.frameRate = frameRate;
		}
	}

	public static final class UnicodeFrame implements VideoQuery.Command, Command, Comparable<UnicodeFrame>,
			Serializable {
		@Getter
		private final int frameNum;
		@Getter
		private final String videoCode, frame;

		public UnicodeFrame(String videoCode, int frameNum, String frame) {
			this.videoCode = videoCode;
			this.frameNum = frameNum;
			this.frame = frame;
		}

		@Override
		public String toString() {
			return String.format("Video %s : Frame %s", videoCode, frameNum);
		}

		@Override
		public int compareTo(UnicodeFrame f) {
			return Integer.compare(this.frameNum, f.frameNum);
		}
	}
}
