package com.campushub.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Applies {@link LogRedaction} to whatever the pattern wraps in {@code %redact(…)}, which in
 * {@code logback-spring.xml} is the message and the stack trace — the only two parts of a log line
 * an identifier can arrive in.
 *
 * <p>Putting it in the layout rather than at the call sites is what makes it a property of the
 * application instead of a rule people remember: a log line written inside Spring, inside a library,
 * or inside code that does not exist yet is redacted on the way out all the same.
 */
public class RedactingConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return LogRedaction.redact(in);
    }
}
