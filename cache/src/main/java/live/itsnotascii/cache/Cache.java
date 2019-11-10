package live.itsnotascii.cache;

import akka.actor.ActorRef;
import akka.actor.UntypedAbstractActor;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import live.itsnotascii.Main;
import live.itsnotascii.core.Constants;
import live.itsnotascii.core.UnicodeVideo;
import live.itsnotascii.messages.RegisterRequest;
import live.itsnotascii.messages.VideoRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cache extends UntypedAbstractActor {
	public static final String REGISTER = "register";
	private final String name;
	private final String location;
	private final String targetHost;
	private ActorRef parent;
	private static final Map<String, UnicodeVideo> cachedVideos = new HashMap<>();

	private Cache(String name, String location, String targetHost) {
		this.name = name;
		this.location = location + getSelf().path().toStringWithoutAddress();
		this.targetHost = targetHost;
	}

	@Override
	public void onReceive(Object message) throws Throwable {
		if (message.equals(REGISTER)) {
			Http http = Http.get(getContext().getSystem());
			HttpRequest request = HttpRequest.create()
					.withUri(targetHost)
					.addHeader(HttpHeader.parse(Constants.HTTP_HEADER_CACHE_REGISTER, location));
			http.singleRequest(request);

			System.out.println("Sending register request: " + location + " to " + targetHost);
		} else if (message instanceof RegisterRequest.Acknowledge) {
			RegisterRequest.Acknowledge acknowledge = (RegisterRequest.Acknowledge) message;
			System.out.println("Received message: " + acknowledge.getMessage());
			this.parent = getSender();
		} else if (message instanceof VideoRequest.Available) {
			VideoRequest.Available request = (VideoRequest.Available) message;
			UnicodeVideo video = findVideo(request.getVideoName());
			getSender().tell(new VideoRequest.Response(request, video), ActorRef.noSender());
		}
	}

	private static UnicodeVideo findVideo(String name) {
		System.out.println("Fetching video " + name);
		if (cachedVideos.containsKey(name))
			return cachedVideos.get(name);

		UnicodeVideo video = null;
		Path dir = null;

		try {
			dir = Paths.get(Main.class.getResource("../../video/" + name + ".txt").toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		if (dir == null)
			dir = Paths.get("/videos/" + name + ".txt");

		try {
			assert dir.toFile().exists();
			InputStream in = Files.newInputStream(dir);
			int len = 0;
			int x;

			List<byte[]> frames = new ArrayList<>();

			while ((x = in.read()) > 0) {
				len += x - '0';
				while ((x = in.read()) != '\n') {
					len *= 10;
					len += x - '0';
				}
				byte[] buffer = new byte[len];
				in.read(buffer);
				frames.add(buffer);
				// empty start of line
				in.read();
				System.out.println("Length: " + len + ", " + new String(buffer).length());
				len = 0;
			}


			cachedVideos.put(name, new UnicodeVideo(name, frames));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return cachedVideos.getOrDefault(name, null);
	}
}
