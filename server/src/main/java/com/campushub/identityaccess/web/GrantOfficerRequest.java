package com.campushub.identityaccess.web;

import jakarta.validation.constraints.NotBlank;

record GrantOfficerRequest(@NotBlank String accountId) {}
