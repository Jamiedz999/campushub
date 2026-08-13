package com.campushub;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(CampusHubApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void moduleDocumentationIsGeneratedFromTheCode() {
        new Documenter(MODULES).writeDocumentation();
    }
}
