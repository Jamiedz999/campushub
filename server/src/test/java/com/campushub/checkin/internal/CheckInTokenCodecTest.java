package com.campushub.checkin.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.checkin.internal.CheckInTokenCodec.TokenStatus;
import com.campushub.checkin.internal.CheckInTokenCodec.Verification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// Fixed clock, known secret, every window boundary — see docs/adr/07-define-qr-checkin-and-anti-fraud.md.
// Nothing about a token is stored, so these tests are the whole proof that the mechanism works: the
// codec is verified by recomputation, and this is where "worthless a minute later" is held to account.
class CheckInTokenCodecTest {

    private static final String SECRET = "test-only-checkin-hmac-secret";
    private static final String EVENT_ID = "68a1b2c3d4e5f60718293a4b";
    private static final Duration WINDOW = Duration.ofSeconds(60);
    // Epoch second 1774000020 divides exactly by 60, so it is a window start and ISSUED_AT sits 20
    // seconds into that window — which makes every boundary below an exact instant rather than a guess.
    private static final Instant WINDOW_STARTED_AT = Instant.ofEpochSecond(1_774_000_020L);
    private static final Instant ISSUED_AT = WINDOW_STARTED_AT.plusSeconds(20);

    private final CheckInTokenCodec codec = new CheckInTokenCodec(SECRET);

    @Test
    void aTokenIssuedNowIsAcceptedInTheSameWindow() {
        String token = codec.issue(EVENT_ID, ISSUED_AT);

        assertThat(codec.verify(token, ISSUED_AT)).isEqualTo(new Verification(TokenStatus.VALID, EVENT_ID));
    }

    @Test
    void aTokenIsAcceptedAtTheFirstAndLastInstantOfItsOwnWindow() {
        String token = codec.issue(EVENT_ID, ISSUED_AT);

        assertThat(codec.verify(token, WINDOW_STARTED_AT).status()).isEqualTo(TokenStatus.VALID);
        assertThat(codec.verify(token, WINDOW_STARTED_AT.plus(WINDOW).minusMillis(1)).status())
                .isEqualTo(TokenStatus.VALID);
    }

    @Test
    void aTokenFromThePreviousWindowIsStillAccepted() {
        String token = codec.issue(EVENT_ID, ISSUED_AT.minus(WINDOW));

        assertThat(codec.verify(token, ISSUED_AT).status()).isEqualTo(TokenStatus.VALID);
    }

    @Test
    void aTokenIsAcceptedUntilTheLastInstantOfTheWindowAfterItsOwn() {
        String token = codec.issue(EVENT_ID, ISSUED_AT);
        Instant lastAcceptedInstant = WINDOW_STARTED_AT.plus(WINDOW).plus(WINDOW).minusMillis(1);

        assertThat(codec.verify(token, lastAcceptedInstant).status()).isEqualTo(TokenStatus.VALID);
    }

    @Test
    void aTokenFromTwoWindowsAgoIsRejectedAsExpired() {
        String token = codec.issue(EVENT_ID, ISSUED_AT);
        Instant firstRejectedInstant = WINDOW_STARTED_AT.plus(WINDOW).plus(WINDOW);

        // A stale code still names its Event: the signature over it verified, so which door it is for
        // is authenticated even though it is no longer fresh.
        assertThat(codec.verify(token, firstRejectedInstant))
                .isEqualTo(new Verification(TokenStatus.EXPIRED, EVENT_ID));
    }

    @Test
    void aTokenFromAWindowThatHasNotArrivedYetIsRejectedAsExpired() {
        String token = codec.issue(EVENT_ID, ISSUED_AT.plus(WINDOW));

        assertThat(codec.verify(token, ISSUED_AT).status()).isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    void aTamperedSignatureIsRejectedAsInvalid() {
        String token = codec.issue(EVENT_ID, ISSUED_AT);
        String tampered = token.substring(0, token.length() - 1) + flip(token.charAt(token.length() - 1));

        // Nothing in an unsigned code can be believed, including which door it claims to be for.
        assertThat(codec.verify(tampered, ISSUED_AT)).isEqualTo(new Verification(TokenStatus.INVALID, null));
    }

    @Test
    void aSignatureLiftedFromAnotherEventIsRejectedAsInvalid() {
        String other = codec.issue("68a1b2c3d4e5f60718293a4c", ISSUED_AT);
        String forged = EVENT_ID + other.substring(other.indexOf('.'));

        assertThat(codec.verify(forged, ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    void aSignatureLiftedFromAnotherWindowIsRejectedAsInvalid() {
        String other = codec.issue(EVENT_ID, ISSUED_AT.minus(WINDOW));
        String signature = other.substring(other.lastIndexOf('.') + 1);
        String forged = EVENT_ID + "." + windowIndexOf(codec.issue(EVENT_ID, ISSUED_AT)) + "." + signature;

        assertThat(codec.verify(forged, ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    void aTokenIssuedUnderADifferentSecretIsRejectedAsInvalid() {
        String token = new CheckInTokenCodec("a-rotated-secret").issue(EVENT_ID, ISSUED_AT);

        assertThat(codec.verify(token, ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    void malformedTokensAreRejectedAsInvalid() {
        assertThat(codec.verify("", ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
        assertThat(codec.verify("not-a-token", ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
        assertThat(codec.verify(EVENT_ID + ".29566667", ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
        assertThat(codec.verify(EVENT_ID + ".not-a-number.signature", ISSUED_AT).status())
                .isEqualTo(TokenStatus.INVALID);
        assertThat(codec.verify("." + windowIndexOf(codec.issue(EVENT_ID, ISSUED_AT)) + ".signature", ISSUED_AT)
                        .status())
                .isEqualTo(TokenStatus.INVALID);
        assertThat(codec.verify(EVENT_ID + ".1.2.3", ISSUED_AT).status()).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    void theCodeRotatesAtTheEndOfTheWindowItWasIssuedIn() {
        assertThat(codec.rotatesAt(ISSUED_AT)).isEqualTo(WINDOW_STARTED_AT.plus(WINDOW));
        assertThat(codec.rotatesAt(WINDOW_STARTED_AT)).isEqualTo(WINDOW_STARTED_AT.plus(WINDOW));
    }

    @Test
    void everyWindowProducesADifferentCode() {
        assertThat(codec.issue(EVENT_ID, ISSUED_AT)).isNotEqualTo(codec.issue(EVENT_ID, ISSUED_AT.plus(WINDOW)));
    }

    private static String windowIndexOf(String token) {
        return token.split("\\.")[1];
    }

    private static char flip(char signatureCharacter) {
        return signatureCharacter == 'A' ? 'B' : 'A';
    }
}
