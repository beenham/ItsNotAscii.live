package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Coordinator;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

public class CacheMain {
	public static void main(String... inputArgs) {
		JCommander commander = JCommander.newBuilder()
				.addObject(Arguments.get())
				.build();

		try {
			commander.parse(inputArgs);
			Arguments args = Arguments.get();
			Config cfg = ConfigFactory.load("application.conf");


			//	font: ANSI Shadow
			Log.wtf("MAIN", "\n" +
					"\n" +
					"██╗████████╗███████╗███╗   ██╗ ██████╗ ████████╗ █████╗ ███████╗ ██████╗██╗██╗   ██╗     ██╗██╗   ██╗███████╗\n" +
					"██║╚══██╔══╝██╔════╝████╗  ██║██╔═══██╗╚══██╔══╝██╔══██╗██╔════╝██╔════╝██║██║   ██║     ██║██║   ██║██╔════╝\n" +
					"██║   ██║   ███████╗██╔██╗ ██║██║   ██║   ██║   ███████║███████╗██║     ██║██║   ██║     ██║██║   ██║█████╗  \n" +
					"██║   ██║   ╚════██║██║╚██╗██║██║   ██║   ██║   ██╔══██║╚════██║██║     ██║██║   ██║     ██║╚██╗ ██╔╝██╔══╝  \n" +
					"██║   ██║   ███████║██║ ╚████║╚██████╔╝   ██║   ██║  ██║███████║╚██████╗██║██║██╗███████╗██║ ╚████╔╝ ███████╗\n" +
					"╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═══╝ ╚═════╝    ╚═╝   ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═╝╚═╝╚═╝╚══════╝╚═╝  ╚═══╝  ╚══════╝\n" +
					"                                                                                                             \n" +
					" ██████╗ █████╗  ██████╗██╗  ██╗███████╗\n" +
					"██╔════╝██╔══██╗██╔════╝██║  ██║██╔════╝\n" +
					"██║     ███████║██║     ███████║█████╗  \n" +
					"██║     ██╔══██║██║     ██╔══██║██╔══╝  \n" +
					"╚██████╗██║  ██║╚██████╗██║  ██║███████╗\n" +
					" ╚═════╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝╚══════╝\n" +
					"                                        \n" +
					args);

			//	Creating Actor System
			ActorSystem<Command> system = ActorSystem.create(Coordinator.create("CacheCoordinator"),
					args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

			//	Sending http request to target
			Http http = Http.get(system.classicSystem());
			HttpRequest request = HttpRequest.create()
					.withUri(Uri.create(args.getTarget()))
					.addHeader(HttpHeader.parse(Constants.REGISTER_REQUEST, "http://" + args.getWebHostname() +
							":" + args.getWebPort()));
			http.singleRequest(request);
		} catch (ParameterException ignored) {
			commander.usage();
		}
	}
}
