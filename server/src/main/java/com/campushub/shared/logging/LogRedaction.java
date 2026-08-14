package com.campushub.shared.logging;

import java.util.regex.Pattern;

/**
 * Masks the two things that identify a person in this system before a log line is written: an email
 * address, and the 24-character hex identifier every account is known by.
 *
 * <p>It is deliberately blunt. It cannot tell a Student id from an Event id — they are the same shape
 * — so it masks both, and the cost is that an Event is no longer named in the logs by its id. That
 * cost is paid on purpose: the thing you actually trace a report by is the correlation id on the
 * response, which {@link com.campushub.shared.web.CorrelationIdFilter} puts on every line, and which
 * identifies a request rather than a person.
 *
 * <p>The masking is here rather than at each call site because it has to hold for log lines nobody
 * has written yet, including the ones inside Spring. Today the application logs almost nothing, so a
 * grep of a journey would come back clean whether this class existed or not — which is exactly why it
 * exists now, while the discipline is still free, and why
 * {@code RedactedLoggingIntegrationTest} proves the masking is wired into the appender rather than
 * only asserting the grep comes back empty.
 */
public final class LogRedaction {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    // Lookarounds rather than \b: a longer hex run — a SHA, a hash, a secret's fingerprint — is not an
    // identifier, and matching its first 24 characters would mangle it into something unreadable while
    // still leaking the rest.
    private static final Pattern OBJECT_ID =
            Pattern.compile("(?<![0-9A-Fa-f])[0-9A-Fa-f]{24}(?![0-9A-Fa-f])");

    private LogRedaction() {}

    public static String redact(String line) {
        String withoutEmails = EMAIL.matcher(line).replaceAll("[redacted-email]");
        return OBJECT_ID.matcher(withoutEmails).replaceAll("[redacted-id]");
    }
}
