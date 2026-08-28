package com.workflow.embed;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class EmbedSpringBeanConstructorContractTest {

    @Test
    void multiConstructorSpringBeansDeclareOneInjectionConstructor() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<Executable> assertions = scanner
                .findCandidateComponents("com.workflow.embed")
                .stream()
                .map(definition -> loadClass(definition.getBeanClassName()))
                .filter(type -> type.getDeclaredConstructors().length > 1)
                .map(type -> (Executable) () -> assertEquals(
                        1,
                        requiredInjectionConstructors(type),
                        () -> type.getName()
                                + " has multiple constructors but does not "
                                + "declare exactly one required @Autowired constructor"))
                .toList();

        assertAll(assertions);
    }

    private static long requiredInjectionConstructors(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .map(constructor -> constructor.getAnnotation(Autowired.class))
                .filter(annotation -> annotation != null && annotation.required())
                .count();
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Cannot load Spring bean " + className, error);
        }
    }
}
