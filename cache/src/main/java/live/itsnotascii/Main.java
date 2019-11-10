package live.itsnotascii;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Cache;

public class Main {
	public static void main(String[] args) throws Exception {
		String host = "127.0.0.1";
		int port = 2553;
		Config cfg = ConfigFactory.parseString("akka.remote.artery.canonical.hostname=" + host + ",akka.remote.artery.canonical.port=" + port)
				.withFallback(ConfigFactory.load());

		String name = "CacheAlpha";
		String sys_name = "CacheSystem";
		String location = "akka://" + sys_name + "@" + cfg.getValue("akka.remote.artery.canonical.hostname").render().replaceAll("\"", "")
				+ ":" + cfg.getValue("akka.remote.artery.canonical.port").render();
		String targetHost = "http://localhost/";
		ActorSystem system = ActorSystem.create(sys_name, cfg);
		ActorRef actor = system.actorOf(Props.create(Cache.class, name, location, targetHost), name);

		actor.tell(Cache.REGISTER, ActorRef.noSender());
	}
}

