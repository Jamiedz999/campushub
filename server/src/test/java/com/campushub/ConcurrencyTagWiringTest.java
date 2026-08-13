package com.campushub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// Empty on purpose, same as the Mongock change unit: proves @Tag("concurrency") is wired into a runnable
// suite from this Issue (mvn -Dgroups=concurrency test), so #17's real concurrency tests tag into something
// that already works rather than inventing the mechanism themselves.
class ConcurrencyTagWiringTest {

    @Test
    @Tag("concurrency")
    void concurrencyTagIsRunnableAsItsOwnSuite() {
        assertThat(true).isTrue();
    }
}
