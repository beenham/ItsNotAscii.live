package live.itsnotascii;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Location;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import live.itsnotascii.cache.Cache;
import live.itsnotascii.cache.CacheManager;
import live.itsnotascii.core.Event;
import live.itsnotascii.main.Listener;
import live.itsnotascii.util.Arguments;

import java.util.concurrent.CompletionStage;

public class Main {
    public static void main(String... inputArgs) {
        JCommander commander = JCommander.newBuilder()
                .addObject(Arguments.get())
                .build();

        try {
            commander.parse(inputArgs);
            Arguments args = Arguments.get();
            Config cfg = ConfigFactory.load();

            //	Creating Actor System
            ActorSystem<Event> system = ActorSystem.create(Listener.create("MainListener"),
                    args.getName(), ConfigFactory.parseMap(args.getOverrides()).withFallback(cfg));

            //	Initializing cluster
            Cluster cluster = Cluster.get(system);
            cluster.manager().tell(Join.create(cluster.selfMember().address()));

        } catch (ParameterException ignored) {
            commander.usage();
        }
    }
}
