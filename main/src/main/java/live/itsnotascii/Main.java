package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.core.CommonMain;
import live.itsnotascii.core.messages.Command;
import live.itsnotascii.main.Coordinator;
import live.itsnotascii.util.Arguments;
import live.itsnotascii.util.Log;

public class Main {
	public static void main(String... inputArgs) {
		Arguments args = CommonMain.parseInputArgs(null, inputArgs);
		if (args == null) return;

		Config cfg = CommonMain.loadConfig();

		// Creating Actor System
		ActorSystem<Command> system = ActorSystem.create(Coordinator.create("MainCoordinator"),
				args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

		// Initializing cluster
		Cluster cluster = Cluster.get(system);
		cluster.manager().tell(Join.create(cluster.selfMember().address()));
	}
}
