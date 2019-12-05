package live.itsnotascii.processor.video;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import live.itsnotascii.processor.frame.FrameProcessor;
import live.itsnotascii.util.Log;

import java.util.ArrayList;
import java.util.List;

public class JCodecVideoProcessor extends VideoProcessor {
	private static final String TAG = JCodecVideoProcessor.class.getCanonicalName();
	private final int workerCount;

	private JCodecVideoProcessor(ActorContext<Command> context, int workerCount) {
		super(context);
		this.workerCount = workerCount;
	}

	public static Behavior<Command> create(int workerCount) {
		return Behaviors.setup(c -> new JCodecVideoProcessor(c, workerCount));
	}

	@Override
	protected VideoProcessor onRequest(GetVideo r) {
		Log.i(TAG, String.format("Request #%s for video %s (%s)", r.request.getId(), r.request.getUrl(), r.request.getCode()));
		List<ActorRef<FrameProcessor.Command>> workers = new ArrayList<>();

		for (int i = 0; i < workerCount; i++)
			workers.add(getContext().spawnAnonymous(FrameProcessor.create()));

		String code = r.request.getCode();
		requests.put(code, r);
		frameWorkers.put(code, workers);
		receivedFrames.put(code, new ArrayList<>());

		JCodecVideoFetcher fetcher = new JCodecVideoFetcher(workers, getContext().getSelf());
		VideoInfo info = fetcher.decodeAndSend(r.request.getCode());

		if (info.frameCount == 0) {
			r.replyTo.tell(new RespondVideo(r.request, null));
			requests.remove(code);
			frameWorkers.get(code).forEach(getContext()::stop);
			frameWorkers.remove(code);
			receivedFrames.remove(code);
			return this;
		}
		Log.i(TAG, String.format("%s frames @ %sfps for video %s", info.frameCount, info.frameRate, code));
		frameAmounts.put(code, info);
		checkIfAllFramesReceived(code);
		return this;
	}
}
