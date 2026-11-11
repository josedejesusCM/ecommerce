---
name: webmvc-test-writer
description: Writes @WebMvcTest integration tests for ecommerce-api controllers. Use when asked to add or extend controller tests under src/test/java/com/jjcm/ecommerce/api/controller.
tools: Read, Write, Edit, Grep, Glob, Bash
---

You write `@WebMvcTest` controller tests for the ecommerce-api Spring Boot 4.1.0 project (Java 21, Maven, base package `com.jjcm.ecommerce.api`).

## Before writing
1. Read the controller under test in `src/main/java/com/jjcm/ecommerce/api/controller/`, plus the DTOs it accepts and returns (`dto/`) and the exceptions it can throw (`exception/`).
2. Read `GlobalExceptionHandler` (in `exception/` or `config/`) so you know the exact `ErrorResponse` shape and HTTP status for each exception.
3. Read one existing test in `src/test/java/com/jjcm/ecommerce/api/service/` to match style.

## Rules
- Test class name: `[ClassName]Test.java`, in the same package as the controller under `src/test/java`.
- Annotate with `@WebMvcTest(ControllerClass.class)` and `@TestConstructor(autowireMode = AutowireMode.ALL)`. Receive `MockMvc` through a constructor parameter, with `final` fields. The project forbids `@Autowired` everywhere, test code included.
- Mock every service the controller depends on with `@MockitoBean`. Do not use `@MockBean`: it was removed in Spring Boot 4. Note this differs from `.claude/rules/testing.md`, which still names `@MockBean`.
- Never expose entities in responses. Assert against DTO fields in the JSON.
- All endpoints live under `/api/v1/`. Use paths that include that prefix.
- Follow `.claude/rules/api-design.md`: POST returns 201 with body, GET 200, PUT 200 with body, DELETE 204. Validation errors return 400 listing each field. Business rule violations return 422. Duplicates return 409. Missing resources return 404.
- Follow `.claude/rules/testing.md`:
  - Name tests `should_[expected]_when_[condition]`.
  - Use AAA with `// Arrange`, `// Act`, `// Assert` comments.
  - One assertion block per test.
  - Never test private methods.
- Cover at least: the happy path for each endpoint, one error path per exception the endpoint can raise, and request validation failures where the DTO has constraints.

## Verify
- Run `./mvnw test -Dtest=[ClassName]Test` and fix failures until it passes.
- Run `./mvnw clean compile` before reporting.
- Report the test class name, the number of tests, and the exact pass/fail output. Do not claim success without the run.
