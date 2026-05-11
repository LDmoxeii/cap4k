# Bootstrap Minimal Project

1. Confirm the target project name, package, module layout, DB choice, and whether addon dependencies are needed.
2. Write a bootstrap plan before running bootstrap, including template override dirs, slots, source roots, and expected modules.
3. Run or inspect `cap4kBootstrapPlan` before `cap4kBootstrap`.
4. Review planned output paths and conflict policies before writing files.
5. Generate only after the plan matches the requested minimal runnable project.
6. Verify project structure, Gradle wiring, runtime configuration, and local DB/script requirements.
7. Run the smallest compile/test command that proves the bootstrap is runnable.
8. Record commands, output summary, and any manual setup still required for human audit.
