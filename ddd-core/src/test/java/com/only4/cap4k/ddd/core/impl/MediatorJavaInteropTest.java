package com.only4.cap4k.ddd.core.impl;

import com.only4.cap4k.ddd.core.Mediator;
import com.only4.cap4k.ddd.core.MediatorSupport;
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator;
import kotlin.reflect.KClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediatorJavaInteropTest {
    private RecordingIdentifierGenerator identifierGenerator;

    @BeforeEach
    void configureIdentifierGenerator() {
        identifierGenerator = new RecordingIdentifierGenerator();
        MediatorSupport.INSTANCE.configure(identifierGenerator);
    }

    @AfterEach
    void releaseIdentifierGenerator() {
        MediatorSupport.INSTANCE.release(identifierGenerator);
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
