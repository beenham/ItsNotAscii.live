package live.itsnotascii.processor.video;

import akka.actor.typed.ActorRef;
import live.itsnotascii.processor.frame.FrameProcessor;
import live.itsnotascii.util.Log;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.Demuxer;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.Format;
import org.jcodec.common.JCodecUtil;
import org.jcodec.common.io.IOUtils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JCodecVideoFetcher extends VideoFetcher {
	private static final String TAG = JCodecVideoFetcher.class.getCanonicalName();

	public JCodecVideoFetcher(List<ActorRef<FrameProcessor.Command>> frameProcessors,
							  ActorRef<VideoProcessor.Command> replyTo) {
		super(frameProcessors, replyTo);
	}

	@Override
	protected VideoProcessor.VideoInfo sendFramesToProcess(String urlString, String videoCode) {
		try {
			long time = System.currentTimeMillis();
			URL url = new URL(urlString);
			File tmpFile = File.createTempFile(String.format("./tmp/%s", videoCode), ".tmp");
			tmpFile.deleteOnExit();
			FileOutputStream out = new FileOutputStream(tmpFile);
			IOUtils.copy(url.openStream(), out);

			Format f = JCodecUtil.detectFormat(tmpFile);
			Demuxer d = JCodecUtil.createDemuxer(f, tmpFile);
			DemuxerTrack vt = d.getVideoTracks().get(0);
			DemuxerTrackMeta dtm = vt.getMeta();

			FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(tmpFile));
			Map<Integer, BufferedImage> frames = new HashMap<>();

			for (int i = 0; i < dtm.getTotalFrames(); i+=2) {
				frames.put(i, AWTUtil.toBufferedImage(grab.getNativeFrame()));
				grab.getNativeFrame();
				if (frames.size() == 5) {
					ActorRef<FrameProcessor.Command> worker = frameProcessors.remove(0);
					Log.v(TAG, String.format("Sending %s frames (%s) to %s", frames.size(), frames.keySet(), worker));
					worker.tell(new FrameProcessor.ProcessFrames(new HashMap<>(frames), videoCode, replyTo));
					frameProcessors.add(worker);
					frames.clear();
				}
			}
			if (frames.size() > 0)
				frameProcessors.get(0).tell(new FrameProcessor.ProcessFrames(new HashMap<>(frames), videoCode, replyTo));

			Log.v(TAG, String.format("Time Used to decode video and send: %sms", (System.currentTimeMillis() - time)));
			return new VideoProcessor.VideoInfo(dtm.getTotalFrames() / 2, Math.round(dtm.getTotalFrames() / (dtm.getTotalDuration() * 2)));
		} catch (Exception e) {
			e.printStackTrace();
			return new VideoProcessor.VideoInfo(0, 0);
		}
	}
}
