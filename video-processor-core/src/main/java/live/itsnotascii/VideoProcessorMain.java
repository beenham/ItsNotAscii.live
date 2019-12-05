package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.core.CommonMain;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.processor.video.Coordinator;
import live.itsnotascii.processor.video.VideoProcessor;
import live.itsnotascii.util.Arguments;

public class VideoProcessorMain {
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
		ActorSystem<Command> system = ActorSystem.create(Coordinator.create(processor,"Listener"),
				args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

		// Sending http request to target
		Http http = Http.get(system.classicSystem());
		HttpRequest request = HttpRequest.create()
				.withUri(Uri.create(args.getTarget()))
				.addHeader(HttpHeader.parse(Constants.REGISTER_REQUEST,
						String.format("http://%s:%s", args.getWebHostname(), args.getWebPort())));
		http.singleRequest(request);
	}
}
