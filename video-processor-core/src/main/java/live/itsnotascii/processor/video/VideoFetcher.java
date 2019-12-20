package live.itsnotascii.processor.video;

import akka.actor.typed.ActorRef;
import live.itsnotascii.processor.frame.FrameProcessor;
import live.itsnotascii.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class VideoFetcher {
	protected static final String STATUS_FAIL = "status=fail";
	protected static final String URL = "url_encoded_fmt_stream_map";
	protected static final String ITAG = "itag";
	private static final String TAG = VideoFetcher.class.getCanonicalName();
	protected final List<String> urlExtensions = Arrays.asList("", "&el=detailpage",
			"&el=embedded&ps=default&eurl=&gl=US&hl=en");
	protected final List<ActorRef<FrameProcessor.Command>> frameProcessors;
	protected final ActorRef<VideoProcessor.Command> replyTo;

	public VideoFetcher(List<ActorRef<FrameProcessor.Command>> frameProcessors,
						ActorRef<VideoProcessor.Command> replyTo) {
		this.frameProcessors = frameProcessors;
		this.replyTo = replyTo;
	}

	public VideoProcessor.VideoInfo decodeAndSend(String videoCode) {
		String url = String.format("https://www.youtube.com/get_video_info?video_id=%s", videoCode);

		String decodedUrl = urlExtensions.stream()
				.map(s -> getVideoURL(url + s))
				.filter(Objects::nonNull)
				.findFirst().orElse("Not Found");

		if (decodedUrl.equals( "NotFound"))
			return new VideoProcessor.VideoInfo(0,0);
		return sendFramesToProcess(decodedUrl, videoCode);
	}

	private String getVideoURL(String url) {
		try {
			String contents = read(request(url));

			if (contents.contains(STATUS_FAIL)) {
				Log.e(TAG, String.format("Status failed. Not a valid video @ %s", contents.indexOf("status=fail")));
				return null;
			} else {
				String streams = Arrays.stream(contents.split("&"))
						.map(kv -> kv.split("="))
						.filter(kv -> kv[0].equals(URL))
						.map(kv -> kv[1])
						.findAny()
						.orElse(null);

				if (streams == null) {
					Log.e(TAG, String.format("%s is null. Not a valid link.", URL));
					return null;
				}

				streams = decodeURL(streams);
				url = Arrays.stream(streams.split(","))
						.map(s -> Arrays.stream(s.split("&"))
								.map(kv -> kv.split("="))
								.collect(Collectors.toMap(kv -> kv[0], kv -> decodeURL(kv[1]))))
						.filter(map -> map.get(ITAG).equals("18"))
						.map(map -> map.get("url"))
						.findAny()
						.orElse(null);
				return url;
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static InputStream request(String url) throws Exception {
		Log.v(TAG, String.format("Requesting connection for %s", url));

		URL u = new URL(url);

		HttpURLConnection connection = (HttpURLConnection) u.openConnection();
		connection.setRequestMethod("GET");

		if (connection.getResponseCode() != 200)
			throw new Exception(String.format("Response from %s is not OK (200)", url));

		return connection.getInputStream();
	}

	private String read(InputStream in) throws Exception {
		Reader reader = new InputStreamReader(in);
		StringWriter writer = new StringWriter();

		char[] buffer = new char[1024];
		int n;
		while ((n = reader.read(buffer)) > -1)
			writer.write(buffer, 0, n);

		reader.close();
		Log.v(TAG, writer.toString());

		return writer.toString();
	}

	protected abstract VideoProcessor.VideoInfo sendFramesToProcess(String url, String videoCode);

	private String decodeURL(String url) {
		return URLDecoder.decode(url, Charset.defaultCharset());
	}
}
