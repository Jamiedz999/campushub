package com.campushub.checkin.internal;

import com.campushub.shared.CheckInSecrets;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The rotating door code: {@code eventId.windowIndex.hmac}, where the window index is the current
 * time divided by 60 seconds and the HMAC covers both halves under a server-side secret. See
 * docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 *
 * <p>Nothing is stored. A code is verified by recomputation, so there is no token table, no cleanup
 * and no state to get out of step — and rotating the secret invalidates every code in flight, which
 * is accepted rather than solved with key versioning.
 *
 * <p>The current window and the one before it are both accepted, so the effective lifetime is
 * between 60 and 120 seconds. That is the smallest value surviving ordinary scan latency and a
 * phone clock that is slightly off.
 */
@Component
class CheckInTokenCodec {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";
    private static final int PART_COUNT = 3;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** Whether a scanned code was signed by this server, and whether it is still fresh. */
    enum TokenStatus {
        VALID,
        INVALID,
        EXPIRED
    }

    /** {@code eventId} is present only on a VALID verification; a rejected code names no Event. */
    record Verification(TokenStatus status, String eventId) {

        static Verification valid(String eventId) {
            return new Verification(TokenStatus.VALID, eventId);
        }

        static Verification rejected(TokenStatus status) {
            return new Verification(status, null);
        }
    }

    private final SecretKeySpec key;

    @Autowired
    CheckInTokenCodec(CheckInSecrets secrets) {
        this(secrets.getHmacSecret());
    }

    // Package-private and secret-by-value: the codec's tests pin every window boundary against a known
    // secret, which is exactly the seam the technical baseline asks this class to be.
    CheckInTokenCodec(String hmacSecret) {
        this.key = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** The code the door screen displays for this instant. Everyone in the room scans the same one. */
    String issue(String eventId, Instant now) {
        long windowIndex = windowIndexOf(now);
        return eventId + SEPARATOR + windowIndex + SEPARATOR + sign(eventId, windowIndex);
    }

    /** When the displayed code stops being the current one — the door screen's countdown. */
    Instant rotatesAt(Instant now) {
        return Instant.ofEpochSecond((windowIndexOf(now) + 1) * WINDOW.toSeconds());
    }

    /**
     * INVALID means the signature is not this server's — a tampered, forged or foreign code, and the
     * only genuinely suspicious outcome. EXPIRED means the signature is ours but the window is not
     * one of the two accepted, which is the ordinary case of a code that rotated mid-scan and is
     * worded to the Student as a normal retry.
     */
    Verification verify(String token, Instant now) {
        String[] parts = token.split("\\" + SEPARATOR, -1);
        if (parts.length != PART_COUNT || parts[0].isEmpty()) {
            return Verification.rejected(TokenStatus.INVALID);
        }
        String eventId = parts[0];
        long windowIndex;
        try {
            windowIndex = Long.parseLong(parts[1]);
        } catch (NumberFormatException notAWindowIndex) {
            return Verification.rejected(TokenStatus.INVALID);
        }
        // Constant-time: the signature is compared before the window is, so a forged code learns
        // nothing from how long the answer took.
        if (!MessageDigest.isEqual(
                sign(eventId, windowIndex).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return Verification.rejected(TokenStatus.INVALID);
        }
        long currentWindow = windowIndexOf(now);
        boolean accepted = windowIndex == currentWindow || windowIndex == currentWindow - 1;
        // A window that has not arrived yet is a phone clock running ahead, not an attack, and the
        // Student's way through is the same as for a stale one: scan the screen again.
        return accepted ? Verification.valid(eventId) : Verification.rejected(TokenStatus.EXPIRED);
    }

    private static long windowIndexOf(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), WINDOW.toSeconds());
    }

    private String sign(String eventId, long windowIndex) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return ENCODER.encodeToString(
                    mac.doFinal((eventId + SEPARATOR + windowIndex).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException unusableSecret) {
            // HmacSHA256 is required of every JDK and the key is fixed at startup, so reaching here
            // means the configured secret can never sign anything — a startup fault, not a scan fault.
            throw new IllegalStateException("Check-in codes cannot be signed with the configured secret.",
                    unusableSecret);
        }
    }
}
