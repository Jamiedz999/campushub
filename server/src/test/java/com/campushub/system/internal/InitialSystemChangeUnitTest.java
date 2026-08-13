package com.campushub.system.internal;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class InitialSystemChangeUnitTest {

    private final InitialSystemChangeUnit changeUnit = new InitialSystemChangeUnit();

    @Test
    void executionAndRollbackAreNoOps() {
        assertThatCode(changeUnit::execution).doesNotThrowAnyException();
        assertThatCode(changeUnit::rollback).doesNotThrowAnyException();
    }
}
