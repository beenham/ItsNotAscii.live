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

	public UnicodeVideo(final String name, final List<byte[]> frames) {
		this.name = name;
		this.frames = frames.stream().map(String::new).collect(Collectors.toList());
	}
}
