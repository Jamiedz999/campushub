package com.campushub.system.web;

import java.time.Instant;

record SystemStatusResponse(String status, String version, Instant buildTime, Instant serverTime) {}
