package com.campushub.event.web;

import com.campushub.event.EventModule.FormField;
import com.campushub.event.EventModule.RegistrationForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

record UpdateRegistrationFormRequest(@NotNull List<@Valid FormField> fields) {

    RegistrationForm toRegistrationForm() {
        return new RegistrationForm(fields);
    }
}
