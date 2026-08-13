package com.campushub;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

// These two rules are what turn "two modules never write the same document" into a build failure
// rather than a sentence — see docs/adr/17-define-code-structure-and-its-enforcement.md. Each rule is
// checked twice: once against the real codebase (expecting no violation) and once against a small fixture
// package built to violate it (expecting the rule to actually catch it) — proof the guard has teeth,
// not just that the production code happens to be clean today.
class DocumentOwnershipRulesTest {

    private static final String PRODUCTION_BASE_PACKAGE = "com.campushub";
    private static final String FIXTURE_BASE_PACKAGE = "archrulesfixtures";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PRODUCTION_BASE_PACKAGE);

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURE_BASE_PACKAGE);

    @Test
    void mongoTemplateIsConfinedToPersistencePackages() {
        mongoTemplateRule().check(PRODUCTION_CLASSES);
    }

    @Test
    void mongoTemplateRuleFailsOnARealViolation() {
        assertThatThrownBy(() -> mongoTemplateRule().check(FIXTURE_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadService")
                .hasMessageContaining("MongoTemplate");
    }

    @Test
    void modulesDoNotReferenceEachOthersDocumentTypes() {
        documentOwnershipRule(PRODUCTION_BASE_PACKAGE).check(PRODUCTION_CLASSES);
    }

    @Test
    void documentOwnershipRuleFailsOnARealViolation() {
        assertThatThrownBy(() -> documentOwnershipRule(FIXTURE_BASE_PACKAGE).check(FIXTURE_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ReachesIntoModuleB")
                .hasMessageContaining("SomeDocument");
    }

    private static ArchRule mongoTemplateRule() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(MongoTemplate.class)
                .because("MongoTemplate access must stay inside a module's persistence package");
    }

    private static ArchRule documentOwnershipRule(String basePackage) {
        return classes()
                .should(new ArchCondition<JavaClass>("not depend on another module's @Document types") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        String ownModule = topLevelModuleOf(javaClass, basePackage);
                        if (ownModule == null) {
                            return;
                        }
                        javaClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                            JavaClass target = dependency.getTargetClass();
                            if (!target.isAnnotatedWith(Document.class)) {
                                return;
                            }
                            String targetModule = topLevelModuleOf(target, basePackage);
                            if (targetModule != null && !ownModule.equals(targetModule)) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        javaClass.getName() + " (module " + ownModule + ") depends on "
                                                + target.getName() + ", a @Document type owned by module "
                                                + targetModule));
                            }
                        });
                    }
                })
                .because("a module owns whole documents; two modules never write the same document");
    }

    private static String topLevelModuleOf(JavaClass javaClass, String basePackage) {
        String prefix = basePackage + ".";
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String remainder = packageName.substring(prefix.length());
        int dot = remainder.indexOf('.');
        return dot == -1 ? remainder : remainder.substring(0, dot);
    }
}
