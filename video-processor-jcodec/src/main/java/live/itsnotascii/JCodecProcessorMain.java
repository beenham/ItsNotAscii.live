package live.itsnotascii;

import live.itsnotascii.processor.video.JCodecVideoProcessor;

public class JCodecProcessorMain {
	public static void main(String... args) {
		VideoProcessorMain.run(JCodecVideoProcessor.create(10), args);
	}
}
