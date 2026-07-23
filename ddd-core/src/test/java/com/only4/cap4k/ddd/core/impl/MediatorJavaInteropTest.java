package com.only4.cap4k.ddd.core.impl;

import com.only4.cap4k.ddd.core.Mediator;
import com.only4.cap4k.ddd.core.MediatorSupport;
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator;
import kotlin.reflect.KClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediatorJavaInteropTest {

    @BeforeEach
    void configureIdentifierGenerator() {
        MediatorSupport.INSTANCE.configure(new RecordingIdentifierGenerator());
    }

    @Test
    void instanceAccessorUsesGetIdentifiers() {
        Mediator mediator = new DefaultMediator();

        assertEquals("ID-java-instance", mediator.getIdentifiers().next("java-instance", String.class));
    }

    @Test
    void staticAccessorUsesGetIdentifierGenerator() {
        assertEquals(
            "ID-java-static",
            Mediator.getIdentifierGenerator().next("java-static", String.class)
        );
    }

    private static final class RecordingIdentifierGenerator implements IdentifierGenerator {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T next(String strategy, KClass<T> type) {
            return (T) ("ID-" + strategy);
        }
    }
}
