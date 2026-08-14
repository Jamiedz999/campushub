package com.campushub.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactionTest {

    @Test
    void anEmailAddressIsMasked() {
        String redacted = LogRedaction.redact("registered ada@campushub.example for the talk");

        assertThat(redacted).doesNotContain("ada@campushub.example").contains("[redacted-email]");
    }

    @Test
    void everyEmailAddressOnOneLineIsMasked() {
        String redacted = LogRedaction.redact("promoted ada@campus.example over grace@campus.example");

        assertThat(redacted).doesNotContain("ada@").doesNotContain("grace@");
    }

    @Test
    void anEmailIsMaskedWhereverItSitsInTheLine() {
        assertThat(LogRedaction.redact("ada@campus.example scanned")).startsWith("[redacted-email]");
        assertThat(LogRedaction.redact("scanned by ada@campus.example")).endsWith("[redacted-email]");
        assertThat(LogRedaction.redact("<ada@campus.example>")).isEqualTo("<[redacted-email]>");
    }

    @Test
    void anAccountIdentifierIsMasked() {
        String redacted = LogRedaction.redact("student 68b1f4c2a91d4e0f3c7a2b19 took the last seat");

        assertThat(redacted).doesNotContain("68b1f4c2a91d4e0f3c7a2b19").contains("[redacted-id]");
    }

    @Test
    void anIdentifierIsMaskedInsideAPathAndInsideQuotes() {
        assertThat(LogRedaction.redact("GET /api/events/68b1f4c2a91d4e0f3c7a2b19/registration"))
                .isEqualTo("GET /api/events/[redacted-id]/registration");
        assertThat(LogRedaction.redact("\"studentId\":\"68b1f4c2a91d4e0f3c7a2b19\""))
                .isEqualTo("\"studentId\":\"[redacted-id]\"");
    }

    @Test
    void anUppercaseIdentifierIsMaskedToo() {
        assertThat(LogRedaction.redact("68B1F4C2A91D4E0F3C7A2B19")).isEqualTo("[redacted-id]");
    }

    // The masking is deliberately blunt — it cannot tell a Student id from an Event id, so it masks
    // both. What it must not do is start eating ordinary words, or the logs stop being readable and
    // someone turns it off.
    @Test
    void ordinaryProseAndShortHexAreLeftAlone() {
        String line = "seat ledger write refused: startsAt has passed (deadbeef, 3 enrolled)";

        assertThat(LogRedaction.redact(line)).isEqualTo(line);
    }

    @Test
    void aCorrelationIdSurvivesBecauseItIdentifiesARequestRatherThanAPerson() {
        String uuid = "9f2c1a44-6e8b-4c17-9a30-51d0e7b4c8f2";

        assertThat(LogRedaction.redact("[" + uuid + "] handled in 12ms")).contains(uuid);
    }

    @Test
    void aLongerHexRunIsNotHalfMaskedByMatchingItsFirstTwentyFourCharacters() {
        String sha = "a".repeat(40);

        assertThat(LogRedaction.redact(sha)).isEqualTo(sha);
    }

    @Test
    void nothingIsMaskedWhenThereIsNothingToMask() {
        assertThat(LogRedaction.redact("Started CampusHubApplication in 3.1 seconds"))
                .isEqualTo("Started CampusHubApplication in 3.1 seconds");
    }

    @Test
    void anEmptyLineIsHandled() {
        assertThat(LogRedaction.redact("")).isEmpty();
    }
}
