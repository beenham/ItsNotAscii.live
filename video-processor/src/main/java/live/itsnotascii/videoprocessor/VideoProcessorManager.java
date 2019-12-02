package live.itsnotascii.videoprocessor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import live.itsnotascii.util.Log;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VideoProcessorManager extends AbstractBehavior<VideoProcessorManager.Command> {
	private static final String TAG = VideoProcessorManager.class.getCanonicalName();

	private final Map<Request, ActorRef<VideoProcessor.Command>> workingRequests;
	private final Map<String, ActorRef<VideoProcessor.Command>> videoProcessors;
	private final Set<ActorRef<VideoProcessor.Command>> workingVideoProcessors;
	private final List<Request> pendingRequests;

	private VideoProcessorManager(ActorContext<Command> context) {
		super(context);
		this.videoProcessors = new HashMap<>();
		this.workingRequests = new HashMap<>();
		this.workingVideoProcessors = new HashSet<>();
		this.pendingRequests = new ArrayList<>();

		//	Create listener for when a VideoProcessor joins the cluster
		ActorRef<Receptionist.Listing> subscriptionAdapter =
				context.messageAdapter(Receptionist.Listing.class, listing ->
						new VideoProcessorsUpdated(listing.getServiceInstances(VideoProcessor.SERVICE_KEY)));
		context.getSystem().receptionist().tell(Receptionist.subscribe(VideoProcessor.SERVICE_KEY, subscriptionAdapter));
	}

	public static Behavior<Command> create() {
		return Behaviors.setup(VideoProcessorManager::new);
	}

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
				.onMessage(VideoProcessorsUpdated.class, this::onVideoProcessorsUpdated)
				.onMessage(Request.class, this::onVideoRequest)
				.onMessage(WrappedRespondVideo.class, this::onVideoResponse)
				.build();
	}

	private VideoProcessorManager onVideoProcessorsUpdated(VideoProcessorsUpdated command) {
		Set<ActorRef<VideoProcessor.Command>> unavailableWorking = workingVideoProcessors.parallelStream()
				.filter(Predicate.not(command.newVideoProcessors::contains))
				.collect(Collectors.toSet());

		if (unavailableWorking.size() > 0) {
			List<Request> newPending = workingRequests.keySet().parallelStream()
					.filter(r -> unavailableWorking.contains(workingRequests.get(r)))
					.collect(Collectors.toList());
			newPending.parallelStream().forEach(workingRequests::remove);

			List<ActorRef<VideoProcessor.Command>> available = command.newVideoProcessors.parallelStream()
					.filter(Predicate.not(workingVideoProcessors::contains))
					.collect(Collectors.toList());

			processPending(newPending, available);
			pendingRequests.addAll(newPending);
		}

		videoProcessors.clear();
		command.newVideoProcessors.forEach(vp -> videoProcessors.put(vp.path().toString(), vp));

		if (pendingRequests.size() > 0) {
			List<ActorRef<VideoProcessor.Command>> available = videoProcessors.values().parallelStream()
					.filter(Predicate.not(workingVideoProcessors::contains))
					.collect(Collectors.toList());

			processPending(pendingRequests, available);
		}

		Log.i(TAG, String.format("List of Video Processors Register: %s", command.newVideoProcessors));
		return this;
	}

	private VideoProcessorManager onVideoRequest(Request r) {
		List<ActorRef<VideoProcessor.Command>> available = videoProcessors.values().parallelStream()
				.filter(Predicate.not(workingVideoProcessors::contains))
				.collect(Collectors.toList());

		if (available.size() > 0) {
			ActorRef<VideoProcessor.Command> worker = available.get(0);
			sendRequest(r, worker);
		} else {
			pendingRequests.add(r);
		}

		return this;
	}

	private VideoProcessorManager onVideoResponse(WrappedRespondVideo r) {
		ActorRef<VideoProcessor.Command> worker = workingRequests.remove(r.response.getRequest());
		workingVideoProcessors.remove(worker);

		Log.v(TAG, String.format("Response for #%s received", r.response.getRequest().id));

		if (pendingRequests.size() > 0)
			sendRequest(pendingRequests.remove(0), worker);

		return this;
	}


	private void processPending(List<Request> pending, List<ActorRef<VideoProcessor.Command>> avail) {
		while (pending.size() > 0 && avail.size() > 0) {
			ActorRef<VideoProcessor.Command> worker = avail.remove(0);
			Request r = pending.remove(0);
			sendRequest(r, worker);
		}
	}

	private void sendRequest(Request r, ActorRef<VideoProcessor.Command> worker) {
		ActorRef<VideoProcessor.RespondVideo> respondVideoAdapter =
				getContext().messageAdapter(VideoProcessor.RespondVideo.class, WrappedRespondVideo::new);
		worker.tell(new VideoProcessor.GetVideo(r, respondVideoAdapter));
		workingRequests.put(r, worker);
		workingVideoProcessors.add(worker);

		Log.v(TAG, String.format("Sending request #%s to %s", r.id, worker));
	}

	public interface Command extends live.itsnotascii.core.messages.Command {}

	private static final class VideoProcessorsUpdated implements Command {
		private final Set<ActorRef<VideoProcessor.Command>> newVideoProcessors;

		public VideoProcessorsUpdated(Set<ActorRef<VideoProcessor.Command>> videoProcessors) {
			this.newVideoProcessors = videoProcessors;
		}
	}

	public static final class Request implements Command, Serializable {
		@Getter
		private final long id;
		@Getter
		private final String url;
		@Getter
		private final String code;

		public Request(long id, String url, String code) {
			this.id = id;
			this.url = url;
			this.code = code;
		}
	}

	private static final class WrappedRespondVideo implements Command {
		private final VideoProcessor.RespondVideo response;

		private WrappedRespondVideo(VideoProcessor.RespondVideo respondVideo) {
			this.response = respondVideo;
		}
	}
}
