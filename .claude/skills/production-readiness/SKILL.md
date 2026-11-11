---
name: production-readiness
description: Run a production readiness review of the ecommerce Spring Boot API and report what blocks go-live. Use this whenever the user asks whether the project is ready for production, deployment, launch, go-live, release, or "ready to ship"; asks for a pre-deploy, hardening, security, or launch checklist; wants a review of config, secrets, database, concurrency, error handling, or observability before going live; or asks what is still missing before real customers use it. Also use it when the user says things like "can we put this online", "what would break in prod", or "is this safe to deploy", even if they never say "production readiness".
compatibility: Requires the Maven wrapper (./mvnw) and Java 21. Build and test steps also need PostgreSQL on localhost:5433 (database ecommerce_db). Static checks work without a database.
---

# Production Readiness Review

Review this project (`com.jjcm.ecommerce.api`, Spring Boot 4.1 + PostgreSQL) and report what stands between it and production. This is a **read-only audit**: report findings and suggested fixes, and do not edit code unless the user asks you to fix something afterwards.

The detailed check catalogue, with the exact grep patterns and how to verify each item, is in `references/checks.md`. Read it before you start, and work through every section.

## Workflow

1. **Read the project rules first.** Read `.claude/CLAUDE.md` and `.claude/rules/*.md`. They define what "correct" means here (DTO-only controllers, `/api/v1/` paths, the status-code table, the exception mapping). Judge the code against these rules, not against general preferences.

2. **Run the build and tests.** Run `./mvnw clean verify` from the project root. Record the result exactly.
   - If the failure is "connection refused" or a database error, the cause is the environment (PostgreSQL isn't running), not the code. Report the build as **NOT VERIFIED**, not as failed, and keep going with the static checks.
   - Note any test that is `@Disabled`, skipped, or empty.

3. **Run the static checks.** Go through every section of `references/checks.md` in order: configuration and secrets, database, API contract, security, error handling, observability, packaging, and documentation drift. For each check, use the grep or read step listed there, then decide PASS, FAIL, or NOT APPLICABLE. Every FAIL needs a file path and line number as evidence.

4. **Run the existing project checks.** The project already has three slash commands. Run their logic and fold the results into the report:
   - `/check-transactions` → write operations missing `@Transactional`
   - `/find-n-plus-one` → N+1 query risks
   - `/verify-dto-coverage` → controller methods that return entities

5. **Assign severity** using the definitions below, then write the report.

## Severity

- **BLOCKER**: must be fixed before any real user or real data touches the system. Examples: live credentials in tracked files, a public API with no authentication, a broken build, or a script that destroys data and could be run against production.
- **MAJOR**: fix before go-live. Examples: data-correctness risks such as lost updates on stock, unbounded list endpoints, or no migration path for the schema.
- **MINOR**: hygiene that should be done soon but does not block launch. Examples: documentation drift, missing graceful shutdown, or a missing catch-all error handler.

Do not inflate severity to make the report look thorough. If something is a deliberate course-project choice (for example, no login yet), say that it is a decision for the user, mark it MAJOR or BLOCKER according to the deployment target, and name the decision. Don't silently fix it.

## Verdict

- Any BLOCKER → **NOT READY**
- No BLOCKER, at least one MAJOR → **READY WITH RISKS**
- Only MINOR or nothing → **READY**

If the build could not run, add a line saying the verdict does not cover test results.

## Report format

Use this structure, in this order:

```
# Production Readiness: <verdict>

Build & tests: <PASS | FAIL | NOT VERIFIED> — <one-line reason>
Findings: <n> blocker · <n> major · <n> minor

## Blockers
| # | Area | Finding | Evidence (file:line) | Suggested fix |

## Major
(same table)

## Minor
(same table)

## Passed checks
<short bulleted list, so the user knows what was checked and is fine>

## Not verified
<anything that needed a running database, a running server, or a decision from the user>
```

Rules for the report:
- **Never print secret values.** If you find a password or token, name the file and line and say it is a credential. Show `***` in place of the value.
- **Every finding needs a location.** A finding without a file and line is an opinion, not evidence.
- **Keep suggested fixes short and concrete**, such as "add `@Version` to `Product` and retry on `ObjectOptimisticLockingFailureException`", not an essay.
- **Don't pad the report.** If a section has no findings, say so in one line under Passed checks.

## Finish

End by asking which findings the user wants fixed first. Offer to fix them in priority order, starting with the blockers. Do not start fixing without being asked.
