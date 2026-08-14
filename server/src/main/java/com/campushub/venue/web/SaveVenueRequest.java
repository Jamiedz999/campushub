package com.campushub.venue.web;

import jakarta.validation.constraints.NotBlank;

record SaveVenueRequest(@NotBlank String name) {}
