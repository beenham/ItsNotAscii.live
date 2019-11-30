package live.itsnotascii.core.messages;

import lombok.Getter;

public class JoinCluster implements Command {
	@Getter
	private final String location;

	public JoinCluster(String location) {
		this.location = location;
	}
}
