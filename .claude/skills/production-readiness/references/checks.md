# Production Readiness Check Catalogue

Work through these sections in order. Each check says what to look for, how to find it, and what counts as a FAIL. Paths are relative to the project root. Base package: `src/main/java/com/jjcm/ecommerce/api/`.

Known starting points for this project are noted as "Hotspot". They are leads to verify by reading the code, not findings to copy into the report. The code may have changed since this file was written.

---

## 1. Configuration & secrets

**1.1 No credentials in tracked files (BLOCKER if found)**
- Grep tracked files for `password`, `secret`, `token`, `apikey`, `api-key`, `private-key`.
  Use `git ls-files` to limit the search to tracked files.
- Hotspot: `src/main/resources/application.properties` sets `spring.datasource.password`. If it holds a real value, it is a BLOCKER: the credential is in version control. Report it as a secret, redacted.
- FAIL if any real credential is committed. A placeholder such as `${DB_PASSWORD}` is a PASS.

**1.2 Credentials come from the environment in production (MAJOR)**
- Production should read the datasource URL, username, and password from environment variables, for example `${DB_URL}`, `${DB_PASSWORD}`.
- FAIL if the only datasource config is hard-coded literals.

**1.3 A production profile exists (MAJOR)**
- Look for `application-prod.properties` or `application-prod.yml`. `.gitignore` already excludes `application-*-local.*`, so the prod profile is expected to be tracked with env-var placeholders.
- FAIL if there is no profile separating dev settings from production settings.

**1.4 SQL logging is off in production (MAJOR)**
- Check `spring.jpa.show-sql` and `spring.jpa.properties.hibernate.format_sql`.
- Hotspot: `application.properties` sets `show-sql=true`. That is fine for development but must be turned off in the production profile. FAIL if no production profile overrides it.

**1.5 Swagger UI is disabled in production (MAJOR)**
- `springdoc.swagger-ui.*` and `springdoc.api-docs.*` are set in `application.properties` with no override.
- Hotspot: `/swagger-ui.html` and `/v3/api-docs` are public. FAIL unless the production profile disables them with `springdoc.api-docs.enabled=false` (or the equivalent) or the team has decided to publish the docs.

**1.6 Actuator exposure is deliberate (MAJOR)**
- The project includes `spring-boot-starter-actuator`. Check for `management.endpoints.web.exposure.include`.
- Hotspot: no `management.*` settings exist. By default only `health` is exposed over HTTP, which is acceptable. FAIL if `include=*` is set, or if sensitive endpoints such as `env`, `heapdump`, or `beans` are exposed without security.

**1.7 Stack traces are not returned to clients (MAJOR)**
- Check `server.error.include-stacktrace` and `server.error.include-message`. Their defaults keep stack traces out of responses, so this passes unless they are explicitly set to `always`.

**1.8 Graceful shutdown (MINOR)**
- Look for `server.shutdown=graceful`. Hotspot: not set. Report as MINOR.

**1.9 Port and profile are not hard-coded for production (MINOR)**
- `server.port=8080` is set. Check that the production profile lets the platform set the port (for example, `${PORT:8080}`).

---

## 2. Build, tests & packaging

**2.1 Build and tests pass (BLOCKER if failing for a code reason)**
- Run `./mvnw clean verify`. Record the result.
- A failure caused by no database (connection refused on port 5433) is NOT VERIFIED, not FAIL.

**2.2 Test coverage of the service layer exists (MINOR)**
- Services have tests in `src/test/java/.../service/`: `CategoryServiceTest`, `CustomerServiceTest`, `OrderServiceTest`, `ProductServiceTest`. Check that each service in `src/main/java/.../service/` has a matching test. Report any gap.
- There are no controller tests (`@WebMvcTest`). Report that as MINOR, since `.claude/rules/testing.md` requires them.

**2.3 Java compiler level matches the declared Java version (MAJOR)**
- Hotspot: `pom.xml` sets `<java.version>21</java.version>`, but the `maven-compiler-plugin` configuration sets `<source>8</source>` and `<target>8</target>`. That produces Java 8 bytecode for a Java 21 application and can cause Java 21 language features to fail to compile. FAIL. Suggested fix: remove the `source`/`target` overrides, or set them to `${java.version}`.

**2.4 Runnable artifact builds (MAJOR if it fails)**
- Run `./mvnw clean package -DskipTests` and confirm a jar is produced in `target/`.

**2.5 Dev-only dependencies are kept out of the artifact (MINOR)**
- Hotspot: `spring-boot-devtools` is a runtime, optional dependency. Spring Boot's repackaging excludes it by default. Confirm it does not appear in the packaged jar's `BOOT-INF/lib/`, or note that it is excluded automatically.

**2.6 No dependency vulnerability scanning (MINOR)**
- Look for `dependency-check` or similar in `pom.xml`. Hotspot: none. Report as MINOR, and say it could not be verified without running the scan.

---

## 3. Database & persistence

**3.1 Schema is not destroyed by any automated path (BLOCKER if it can run against prod)**
- Hotspot: `db/schema.sql` begins with `DROP DATABASE IF EXISTS ecommerce_db WITH (FORCE);`. Running it against a live database deletes all data. Confirm it is only documented as a local setup script and is not wired into startup or any deployment step.
- FAIL if any script, config, or startup hook runs it automatically.

**3.2 A migration path exists (MAJOR)**
- `spring.jpa.hibernate.ddl-auto=validate` is correct: Hibernate does not change the schema. But nothing applies schema changes after the first setup. Look for Flyway or Liquibase in `pom.xml` and for a `db/migration` or `db/changelog` folder.
- Hotspot: neither exists. FAIL: production schema changes are manual, and `schema.sql` is a one-time full rebuild, not a migration.

**3.3 Schema and entities agree (MAJOR if they do not)**
- `ddl-auto=validate` makes startup fail on mismatch. If a database is available, start the app and confirm the context loads. Otherwise, compare each `@Table`, `@Column`, and `@JoinColumn` in `src/main/java/.../entity/` with `db/schema.sql` by reading them side by side.
- Hotspot: `OrderItem.subtotal` is mapped and computed in the entity, so it is a PASS. Re-check it only if the mapping changes. Note that an unmapped database column does not fail `validate`, so check for columns in `schema.sql` that no entity uses.

**3.4 Indexes support the query patterns (MINOR)**
- `schema.sql` creates indexes on the foreign-key columns, which PostgreSQL does not do automatically. Confirm every FK in the schema has an index. Hotspot: `payments.order_id` is covered by its unique constraint.

**3.5 Stock updates are safe under concurrency (MAJOR)**
- `OrderService.create` reads `product.getStockQuantity()`, checks it, and writes back `stock - quantity`. Two concurrent orders can both read the same stock value and both succeed, so stock goes negative or is overwritten (lost update).
- Grep `src/main/java/.../entity/Product.java` for `@Version`. Hotspot: no `@Version` exists. Also check for `@Lock(LockModeType.PESSIMISTIC_WRITE)` in `ProductRepository`.
- FAIL unless one of these protections exists, or the team has explicitly accepted the risk.

**3.6 Writes are transactional (MAJOR)**
- Run `/check-transactions`. Confirm each write method in a service has `@Transactional`.

**3.7 No N+1 queries (MAJOR)**
- Run `/find-n-plus-one`. Hotspot to verify: `OrderService.findAll` loads every order and filters by status in memory, which combines with lazy associations in `OrderResponse.from`. Check whether `OrderResponse` touches lazy collections outside a transaction, and whether the repository uses `@EntityGraph` or a `JOIN FETCH`.

**3.8 List endpoints are bounded (MAJOR)**
- Look at every `GET` list method (`findAll` in each service, and `@GetMapping` with no id). Hotspot: each list returns the whole table with no `Pageable`. FAIL unless pagination or an explicit limit exists. Report as MAJOR for a production API.

**3.9 Money is never stored as floating point (MAJOR if violated)**
- Grep entities and DTOs for `double` or `float` in price, amount, or total fields. Expect `BigDecimal` with `NUMERIC(10,2)` or `NUMERIC(12,2)` in the schema.

**3.10 Open-in-view is off (PASS if so)**
- Hotspot: `spring.jpa.open.in.view=false` is set. PASS. Confirm no code depends on lazy loading in the controller layer.

---

## 4. API contract

**4.1 Controllers return DTOs only (BLOCKER for public endpoints)**
- Run `/verify-dto-coverage`. Every controller method must return a DTO, `ResponseEntity<DTO>`, or `List<DTO>`.

**4.2 Paths follow the `/api/v1/` rule (MAJOR)**
- Grep `@RequestMapping` on each controller. Each must start with `/api/v1/`. Hotspot: all four controllers do.

**4.3 Status codes match `.claude/rules/api-design.md` (MAJOR)**
- POST create → `201 Created` with body.
- GET → `200 OK`.
- PUT update → `200 OK` with body.
- DELETE → `204 No Content`.
- Grep the controllers for `HttpStatus.CREATED`, `noContent()`, and `ResponseEntity.ok`. Confirm each `@DeleteMapping` returns 204 and each `@PostMapping` returns 201. Report each mismatch with its line number.
- Hotspot: all four controllers return 201 on create, 200 on get and update, and 204 on delete. PASS. Re-check after any controller change.

**4.4 Every request body is validated (MAJOR)**
- Every `@RequestBody` parameter must have `@Valid`. Grep `@RequestBody` and check for `@Valid` on the same line. Hotspot: all controller request bodies have it.
- Check the request DTOs in `src/main/java/.../dto/` for constraints (`@NotNull`, `@NotBlank`, `@Size`, `@Positive`, and so on). A DTO with no constraints at all is a finding.

**4.5 Validation limits match the database (MAJOR)**
- Compare `@Size(max = ...)` on request DTOs with the column lengths in `db/schema.sql`. For example, `sku` is `VARCHAR(100)`, `name` is `VARCHAR(200)`, `email` is `VARCHAR(255)`. If DTO limits are looser than the column, a request that passes validation will fail at the database with a 500. FAIL on any mismatch.

**4.6 Unique-constraint violations return 409, not 500 (MAJOR)**
- The database enforces unique SKUs, emails, category names, and slugs. `GlobalExceptionHandler` maps `DuplicateResourceException` to 409, but nothing maps `DataIntegrityViolationException`.
- Check whether each service checks for duplicates before saving (for example, `existsBy...`). If it relies on the database constraint alone, a race or a missed check becomes a 500. FAIL if a service can hit the constraint without a pre-check.
- Hotspot: `CustomerService` pre-checks email with `existsByEmailIgnoreCase`. Verify the same for category `name` and `slug` in `CategoryService`, and product `sku` in `ProductService`. Pre-checks narrow the race but do not remove it, so a 500 under concurrent duplicate inserts is a MINOR note, not a FAIL.

**4.7 Foreign-key deletes return a meaningful status (MAJOR)**
- Hotspot: deletes are guarded. `ProductService` checks `orderItemRepository.existsByProductId`, `CategoryService` checks `productRepository.existsByCategoryId`, and `CustomerService` checks `orderRepository.existsByCustomerId`. Each throws `BusinessRuleException` (422). PASS. Confirm the same guard exists on any new delete path.

---

## 5. Security

**5.1 Authentication exists for any non-local deployment (BLOCKER for public deployment)**
- Grep `pom.xml` for `spring-boot-starter-security` and `spring-security`. Hotspot: no security dependency exists, so every endpoint, including order status and payment status changes, is public.
- This is a decision for the user, not something to silently fix. Report it as BLOCKER if the target is public, and name the decision: which endpoints need authentication, and whether this is intentionally out of scope for the course project.

**5.2 Authorization is enforced where the API implies it (MAJOR)**
- `AccessDeniedException` exists in `exception/` and maps to 403 in `GlobalExceptionHandler`. Grep for `new AccessDeniedException(`. Hotspot: nothing throws it, so the 403 mapping is unreachable. Report that the authorization rule is not implemented.

**5.3 Customers cannot read or change other customers' orders (MAJOR, depends on 5.1)**
- `GET /api/v1/orders?customerId=` and `GET /api/v1/orders/{id}` return order data with no ownership check. Report as MAJOR, and note it is moot until 5.1 is decided.

**5.4 CORS is configured only if browsers call the API (MINOR)**
- Grep for `CorsConfigurationSource`, `addCorsMappings`, or `@CrossOrigin`. Hotspot: none. Report as a decision if a frontend is planned.

**5.5 Input sizes are bounded (MAJOR)**
- Confirm the request DTO constraints from 4.5 cover string length, and that quantity fields have a maximum (for example, `@Max`). An unbounded `quantity` lets a single order request large stock reservations.

**5.6 Sensitive data is not logged (MAJOR if violated)**
- Grep `log.` (Lombok `@Slf4j`) statements for `email`, `address`, `password`, `phone`, `payment`. Hotspot: the only log statement in `OrderService` logs product name and stock, which is acceptable.

---

## 6. Error handling

**6.1 Every documented exception maps to its HTTP status (PASS if all present)**
- `GlobalExceptionHandler` must handle `ResourceNotFoundException` (404), `BusinessRuleException` (422), `DuplicateResourceException` (409), and `AccessDeniedException` (403), returning `ErrorResponse`. Hotspot: all four are present.

**6.2 A catch-all handler exists (MAJOR)**
- Look for `@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler`. Hotspot: none. Without it, unexpected errors fall through to Spring's default error page or JSON, which is not the `ErrorResponse` shape the API promises. Suggested fix: add a handler that logs the exception with its stack trace server-side and returns a generic 500 `ErrorResponse` with no internal details.

**6.3 Error responses have a single shape (MINOR)**
- Confirm every error path returns `ErrorResponse`. Spring's own errors (for example, 405 Method Not Allowed, 415 Unsupported Media Type, and 404 for an unknown route) return Spring's default body, not `ErrorResponse`. Report as MINOR and suggest a fix only if the team cares about a uniform error shape.

**6.4 Validation errors list every field (PASS if so)**
- `handleValidation` returns all field errors in `violations`. Confirm this is still true.

---

## 7. Observability & operations

**7.1 Health endpoint is available for the platform (MAJOR)**
- Actuator exposes `/actuator/health` by default. Confirm it is reachable after startup. Health checks for liveness and readiness are a MINOR addition.

**7.2 Logs are configured for production (MINOR)**
- No logging configuration exists. Confirm the default level is acceptable, or that the production profile sets `logging.level.root`.
- Check that `logs/` and `*.log` are in `.gitignore`. Hotspot: they are.

**7.3 Metrics are exported (MINOR)**
- `spring-boot-starter-actuator` is present but no metrics backend is. Report as MINOR, and say it depends on where the app will run.

**7.4 Containerisation or a run recipe exists (MINOR)**
- Look for a `Dockerfile` or a documented run command. Hotspot: none exists. Report as MINOR.

---

## 8. Documentation & drift

**8.1 `.claude/CLAUDE.md` matches the code (MINOR)**
- It says Spring Boot 4.1.0. Hotspot: `pom.xml` says `4.1.2`.
- It lists the base package as `com.jjcm.ecommerce`. The code uses `com.jjcm.ecommerce.api`. Report the drift.

**8.2 The global `CLAUDE.md` matches the project (MINOR)**
- The user's global instructions name the base package as `com.example.ecommerce`, which does not match this project. Mention it as a note, because it can mislead future work.

**8.3 A README exists with run instructions (MINOR)**
- Hotspot: no README is tracked. Report it. The run command is `./mvnw spring-boot:run`, and the database setup is `db/schema.sql` run through `psql`. Both belong in a README.

---

## Checks that cannot run here

Some items need a running system or a human decision. Put them in the "Not verified" section, not the findings:
- The app starts with a real database (`ddl-auto=validate` passing).
- Concurrency behaviour under real parallel requests.
- Load and performance.
- Authentication design (a decision, see 5.1).
- Deployment target and hosting, which decide how severe the security and secret findings are.
