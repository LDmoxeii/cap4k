# Final Review Fix Report

## Java Interop

- Kotlin continues to expose `Mediator.identifiers` for instance and companion access.
- The compiled Java APIs are `mediator.getIdentifiers()` and `Mediator.getIdentifierGenerator()`.
- A spec-exact static `Mediator.getIdentifiers()` cannot coexist with the instance `getIdentifiers()` on the same JVM interface: the two accessors have the same JVM method name and descriptor. The Java interop test therefore compiles and executes the final documented API instead.

## Custom Mediator Initialization

- Added an unconditional `mediatorSupportInitializer` bean in `MediatorAutoConfiguration`.
- It configures `MediatorSupport` with the resolved `Mediator`, `ApplicationContext`, and `IdentifierGenerator`, including when the conditional default mediator bean backs off for a user-supplied mediator.
- The default mediator bean remains conditional and `ddd-core` remains free of JPA coupling.

## TDD Evidence

- The custom-mediator `ApplicationContextRunner` test was written before the initializer. With the corrected auto-configuration test setup it failed as expected with `UninitializedPropertyAccessException` when the default `Mediator.identifiers` getter read the unconfigured support property.
- After adding the initializer, the test passed and verifies all three support values, including the context's `IdentifierGenerator`.

## Verification

- `./gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest"`
- `./gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.MediatorJavaInteropTest"`
- `./gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.AutoConfigurationContextTest"`
- `git diff --check`
