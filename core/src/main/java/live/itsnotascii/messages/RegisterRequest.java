package live.itsnotascii.messages;

import java.io.Serializable;
import lombok.Getter;


public class RegisterRequest implements Serializable {
	private RegisterRequest() {}

	public static class Cache implements Serializable {
		@Getter private final String location;

		public Cache(String location) {
			this.location = location;
		}
	}

	public static class Acknowledge implements Serializable {
		@Getter private final String message;

		public Acknowledge(String message) {
			this.message = message;
		}
	}
}
