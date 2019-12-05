package live.itsnotascii.core;

import java.util.regex.Pattern;

public class Regex {
	public static final Pattern VIDEO_CODE = Pattern.compile("[\\w-_]{11}");
	public static final Pattern YOUTUBE_LINK = Pattern.compile(
			"(?:https?:\\/\\/)?(?:www\\.)?youtu\\.?be(?:\\.com)?\\/?.*(?:watch|embed)?(?:.*v=|v\\/|\\/)(?<link>[\\w-_]{11})");
}
