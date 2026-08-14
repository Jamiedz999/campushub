package com.campushub.checkin.web;

import jakarta.validation.constraints.NotBlank;

// The scanned code, exactly as it was displayed. The Student is identified by their session, never by
// anything in this body — see docs/adr/07-define-qr-checkin-and-anti-fraud.md.
record ScanRequest(@NotBlank String token) {}
