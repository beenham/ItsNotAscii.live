package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.main.Coordinator;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

public class Main {
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
					"System: \t\t" + args.getName() + "\n" +
					"System Server: \t" + args.getHostname() + ":" + args.getPort() + "\n" +
					"Web Server: \t" + args.getWebHostname() + ":" + args.getWebPort() + "\n");

			//	Creating Actor System
			ActorSystem<Command> system = ActorSystem.create(Coordinator.create("MainCoordinator"),
					args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

			//	Initializing cluster
			Cluster cluster = Cluster.get(system);
			cluster.manager().tell(Join.create(cluster.selfMember().address()));

		} catch (ParameterException ignored) {
			commander.usage();
		}
	}
}
