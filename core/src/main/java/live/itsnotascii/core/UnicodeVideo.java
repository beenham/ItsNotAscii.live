package live.itsnotascii.core;

import lombok.Getter;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class UnicodeVideo implements Serializable {
	@Getter
	private final String name;
	@Getter
	private final List<String> frames;
	@Getter
	private final double frameRate;

	public UnicodeVideo(final String name, final List<byte[]> frames, double frameRate) {
		this.name = name;
		this.frames = frames.stream().map(String::new).collect(Collectors.toList());
		this.frameRate = frameRate;
	}
}
