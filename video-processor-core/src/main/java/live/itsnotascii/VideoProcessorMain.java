package live.itsnotascii;

import akka.actor.AddressFromURIString;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.JoinSeedNodes;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.core.CommonMain;
import live.itsnotascii.core.Constants;
import live.itsnotascii.processor.video.VideoProcessor;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

import java.util.Collections;

public class VideoProcessorMain {
	private static final String TAG = VideoProcessorMain.class.getCanonicalName();

	private static final String SUB_TITLE =
			"██╗   ██╗██╗██████╗ ███████╗ ██████╗     ██████╗ ██████╗  ██████╗  ██████╗███████╗███████╗███████╗ ██████╗ ██████╗ \n" +
			"██║   ██║██║██╔══██╗██╔════╝██╔═══██╗    ██╔══██╗██╔══██╗██╔═══██╗██╔════╝██╔════╝██╔════╝██╔════╝██╔═══██╗██╔══██╗\n" +
			"██║   ██║██║██║  ██║█████╗  ██║   ██║    ██████╔╝██████╔╝██║   ██║██║     █████╗  ███████╗███████╗██║   ██║██████╔╝\n" +
			"╚██╗ ██╔╝██║██║  ██║██╔══╝  ██║   ██║    ██╔═══╝ ██╔══██╗██║   ██║██║     ██╔══╝  ╚════██║╚════██║██║   ██║██╔══██╗\n" +
			" ╚████╔╝ ██║██████╔╝███████╗╚██████╔╝    ██║     ██║  ██║╚██████╔╝╚██████╗███████╗███████║███████║╚██████╔╝██║  ██║\n" +
			"  ╚═══╝  ╚═╝╚═════╝ ╚══════╝ ╚═════╝     ╚═╝     ╚═╝  ╚═╝ ╚═════╝  ╚═════╝╚══════╝╚══════╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝";

	public static void run(Behavior<VideoProcessor.Command> processor, String... inputArgs) {
		Arguments args = CommonMain.parseInputArgs(SUB_TITLE, inputArgs);
		if (args == null) return;

		Config cfg = CommonMain.loadConfig();

		// Creating Actor System
		ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(),
				args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));
		system.systemActorOf(processor, "VideoProcessor", Props.empty());

		// Sending http request to target
		HttpRequest request = HttpRequest.create()
				.withUri(Uri.create(args.getTarget()))
				.addHeader(HttpHeader.parse(Constants.REGISTER_REQUEST, "http://" + args.getWebHostname() +
						":" + args.getWebPort()));

		Http.get(system.classicSystem()).singleRequest(request).thenAccept(r -> {
			Log.i(TAG, String.format("Response Received: %s", r));
			if (r.getHeaders() != null) {
				if (r.getHeader(Constants.REGISTER_ACCEPT).isPresent()) {
					String location = system + "@" + r.getHeader(Constants.REGISTER_ACCEPT).get().value();

					Log.v(TAG, String.format("Connecting to %s", location));
					Cluster cluster = Cluster.get(system);
					cluster.manager()
							.tell(new JoinSeedNodes(Collections.singletonList(AddressFromURIString.parse(location))));
				}
			}
		});
	}
}
