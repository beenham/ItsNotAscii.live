package live.itsnotascii.messages;

import live.itsnotascii.core.UnicodeVideo;
import lombok.Getter;

import java.io.Serializable;

public class VideoRequest implements Serializable {
	public static class Available implements Serializable {
		@Getter
		private final long id;
		@Getter
		private final String videoName;

		public Available(final long id, final String videoName) {
			this.id = id;
			this.videoName = videoName;
		}
	}

	public static class Response implements Serializable {
		@Getter
		private final Available request;
		@Getter
		private final UnicodeVideo video;

		public Response(Available request, UnicodeVideo video) {
			this.request = request;
			this.video = video;
		}
	}
}
