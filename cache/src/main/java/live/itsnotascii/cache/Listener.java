package live.itsnotascii.cache;

import akka.actor.AddressFromURIString;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.JoinSeedNodes;
import live.itsnotascii.core.Event;
import live.itsnotascii.core.JoinCluster;

import java.util.Collections;

public class Listener extends AbstractBehavior<Event> {
    private final String id;

    private Listener(ActorContext<Event> context, String id) {
        super(context);
        this.id = id;
        context.getLog().info("Listener {} started", this.id);

        context.spawn(Cache.create("Cache"), "Cache");
    }

    public static Behavior<Event> create(String id) {
        return Behaviors.setup(c -> new Listener(c, id));
    }

    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(JoinCluster.class, this::onJoinCluster)
                .onSignal(PostStop.class, s -> onPostStop())
                .build();
    }

    private Listener onPostStop() {
        getContext().getLog().info("Listener {} stopped.", this.id);
        return this;
    }

    private Listener onJoinCluster(JoinCluster r) {
        String location = getContext().getSystem() + "@" + r.getLocation();

        Cluster cluster = Cluster.get(getContext().getSystem());
        cluster.manager().tell(new JoinSeedNodes(
                Collections.singletonList(AddressFromURIString.parse(location))));
        return this;
    }
}
