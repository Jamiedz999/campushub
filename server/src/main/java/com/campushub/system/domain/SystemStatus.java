package com.campushub.system.domain;

import java.time.Instant;

public record SystemStatus(String status, String version, Instant buildTime, Instant serverTime) {}
