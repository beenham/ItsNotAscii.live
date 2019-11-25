package live.itsnotascii.core;

import lombok.Getter;

public class JoinCluster implements Event {
	@Getter
	private final String location;

	public JoinCluster(String location) {
		this.location = location;
	}
}
