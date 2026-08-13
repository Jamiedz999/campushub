package com.campushub.registration.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record SubmitRegistrationRequest(Map<String, Object> answers) {

    Map<String, Object> answerMap() {
        return answers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }
}
