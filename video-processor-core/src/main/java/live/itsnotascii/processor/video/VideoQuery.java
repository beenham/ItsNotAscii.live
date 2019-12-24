package live.itsnotascii.processor.video;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.core.messages.HttpResponses;
import live.itsnotascii.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VideoQuery extends AbstractBehavior<VideoQuery.Command> {
	private static final String TAG = VideoQuery.class.getCanonicalName();

	private final VideoProcessorManager.Request request;
	private final ActorRef<VideoProcessor.RespondVideo> replyTo;
	private final ActorRef<VideoProcessor.Command> worker;
	private final List<VideoProcessor.UnicodeFrame> receivedFrames;
	private VideoProcessor.VideoInfo videoInfo;

	private VideoQuery(ActorContext<Command> context, VideoProcessorManager.Request request,
					   ActorRef<VideoProcessor.RespondVideo> replyTo, ActorRef<VideoProcessor.Command> worker) {
		super(context);
		this.request = request;
		this.replyTo = replyTo;
		this.worker = worker;
		this.receivedFrames = new ArrayList<>();
		worker.tell(new VideoProcessor.GetVideo(request, getContext().getSelf()));
	}

	public static Behavior<Command> create(VideoProcessorManager.Request request,
										   ActorRef<VideoProcessor.RespondVideo> replyTo,
										   ActorRef<VideoProcessor.Command> worker) {
		return Behaviors.setup(c -> new VideoQuery(c, request, replyTo, worker));
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(VideoProcessor.VideoInfo.class, this::onVideoInfo)
				.onMessage(VideoProcessor.UnicodeFrame.class, this::onUnicodeFrame)
				.onSignal(PostStop.class, s -> this.onPostStop())
				.build();
	}

	private Behavior<Command> onVideoInfo(VideoProcessor.VideoInfo info) {
		this.videoInfo = info;
		return replyWhenAllFramesReceived();
	}

	private Behavior<Command> onUnicodeFrame(VideoProcessor.UnicodeFrame frame) {
		this.receivedFrames.add(frame);
		Log.v(TAG, String.format("Received frame %s for %s", frame.getFrameNum(), frame.getVideoCode()));
		return replyWhenAllFramesReceived();
	}

	private Behavior<Command> replyWhenAllFramesReceived() {
		if (videoInfo != null && receivedFrames.size() == videoInfo.frameCount) {
			if (videoInfo.frameCount == 0) {
				replyTo.tell(new VideoProcessor.RespondVideo(request, new UnicodeVideo(request.getCode(),
						Collections.singletonList(HttpResponses.NOT_FOUND.getBytes()), 1)));
			} else {
				List<byte[]> frames = receivedFrames.stream()
						.sorted()
						.map(VideoProcessor.UnicodeFrame::getFrame)
						.map(String::getBytes)
						.collect(Collectors.toList());

				replyTo.tell(new VideoProcessor.RespondVideo(request,
						new UnicodeVideo(request.getCode(), frames, videoInfo.frameRate)));
				worker.tell(new VideoProcessor.Completed(request.getCode()));
				Log.v(TAG, String.format("Finished processing #%s for video %s", request.getId(), request.getCode()));
			}
			return Behaviors.stopped();
		}
		return this;
	}

	private Behavior<Command> onPostStop() {
//		Log.v(TAG, String.format("Stopping %s", getContext().getSelf()));
		return Behaviors.stopped();
	}

	public interface Command extends live.itsnotascii.core.messages.Command {}
}
