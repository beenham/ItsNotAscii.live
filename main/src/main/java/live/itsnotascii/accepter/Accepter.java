package live.itsnotascii.accepter;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedAbstractActor;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.messages.RegisterRequest;
import live.itsnotascii.messages.VideoRequest;

import java.util.HashMap;
import java.util.Map;

public class Accepter extends UntypedAbstractActor {
	private String cacheLocation;
	public static final Map<Long, UnicodeVideo> requests = new HashMap<>();

	@Override
	public void onReceive(Object message) throws Throwable {
		if (message instanceof RegisterRequest.Cache) {
			ActorRef sender = getSender();
			RegisterRequest.Cache cacheReq = (RegisterRequest.Cache) message;
			cacheLocation = cacheReq.getLocation();
			System.out.println("Registering Cache: " + cacheReq.getLocation());
			ActorSelection selection = getContext().actorSelection(cacheReq.getLocation());
			selection.tell(new RegisterRequest.Acknowledge("Acknowledged by: " + getSelf().toString()), getSelf());
		} else if (message instanceof VideoRequest.Available) {
			VideoRequest.Available req = (VideoRequest.Available) message;
			System.out.println("Sending to cache.");
			getContext().actorSelection(cacheLocation).tell(req, getSelf());
		} else if (message instanceof VideoRequest.Response) {
			VideoRequest.Response response = (VideoRequest.Response) message;
			System.out.println("Video Response received.");
			if (response.getVideo() != null)
				requests.put(response.getRequest().getId(), response.getVideo());
		}
	}
}
