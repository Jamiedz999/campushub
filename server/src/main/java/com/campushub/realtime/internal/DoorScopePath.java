package com.campushub.realtime.internal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scope is the URL. One socket watches one Event's door, so there is no subscribe message, no
 * client-chosen topic and nothing to unsubscribe from — a client that wants a different scope opens a
 * different socket and is authorized again at that handshake.
 *
 * <p>That matters more than the code it saves: an in-band subscribe would be a client-supplied
 * identifier arriving after the only point where a session is authorized, which is exactly the shape
 * of the bug the acceptance criteria name.
 */
final class DoorScopePath {

    /** The Ant pattern the handler is registered under; the group below is the same path, parsed. */
    static final String PATTERN = "/ws/events/*/attendance";

    private static final Pattern DOOR_SCOPE = Pattern.compile("^/ws/events/([^/]+)/attendance$");

    private DoorScopePath() {}

    static Optional<String> eventIdIn(String path) {
        Matcher matcher = DOOR_SCOPE.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
