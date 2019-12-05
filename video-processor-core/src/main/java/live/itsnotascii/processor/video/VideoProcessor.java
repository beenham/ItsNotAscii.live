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
import java.util.stream.Collectors;

public abstract class VideoProcessor extends AbstractBehavior<VideoProcessor.Command> {
	private static final String TAG = VideoProcessor.class.getCanonicalName();
	public static ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "VideoProcessor");

	protected final Map<String, List<ActorRef<FrameProcessor.Command>>> frameWorkers;
	protected final Map<String, List<UnicodeFrame>> receivedFrames;
	protected final Map<String, VideoInfo> frameAmounts;
	protected final Map<String, GetVideo> requests;

	protected VideoProcessor(ActorContext<Command> context) {
		super(context);
		this.frameWorkers = new HashMap<>();
		this.receivedFrames = new HashMap<>();
		this.frameAmounts = new HashMap<>();
		this.requests = new HashMap<>();

		Log.i(TAG, String.format("I'm alive! %s", context.getSelf()));
		context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf().narrow()));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(GetVideo.class, this::onRequest)
				.onMessage(UnicodeFrame.class, this::onFrameReceived)
				.build();
	}

	protected abstract VideoProcessor onRequest(GetVideo r);

	private VideoProcessor onFrameReceived(UnicodeFrame frame) {
		String videoCode = frame.videoCode;
		receivedFrames.get(videoCode).add(frame);
		checkIfAllFramesReceived(videoCode);
		return this;
	}

	protected void checkIfAllFramesReceived(String videoCode) {
		VideoInfo info = frameAmounts.getOrDefault(videoCode, null);
		List<UnicodeFrame> currentFrames = receivedFrames.get(videoCode);
		if (info != null && currentFrames.size() == info.frameCount) {
			GetVideo request = requests.get(videoCode);
			List<byte[]> frames = currentFrames.stream()
					.filter(f -> f.videoCode.equals(request.request.getCode()))
					.sorted()
					.map(UnicodeFrame::getFrame)
					.map(String::getBytes)
					.collect(Collectors.toList());

			request.replyTo.tell(new RespondVideo(request.request,
					new UnicodeVideo(request.request.getCode(), frames, info.frameRate)));

			frameWorkers.get(videoCode).forEach(getContext()::stop);
			frameWorkers.remove(videoCode);
			requests.remove(videoCode);
			receivedFrames.remove(videoCode);
			frameAmounts.remove(videoCode);
		}
	}

	public interface Command extends live.itsnotascii.core.messages.Command {
	}

	static final class GetVideo implements Command, Serializable {
		protected final VideoProcessorManager.Request request;
		protected final ActorRef<RespondVideo> replyTo;

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

	public static final class VideoInfo {
		protected final int frameCount;
		protected final double frameRate;

		public VideoInfo(int frameCount, double frameRate) {
			this.frameCount = frameCount;
			this.frameRate = frameRate;
		}
	}

	public static final class UnicodeFrame implements Command, Comparable<UnicodeFrame> {
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
			if (f.frameNum < this.frameNum)
				return 1;
			else if (f.frameNum > this.frameNum)
				return -1;
			return 0;
		}
	}
}
