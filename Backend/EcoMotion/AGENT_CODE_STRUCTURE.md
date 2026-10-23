# AGENT_CODE_STRUCTURE.md

## 1. Purpose and Priority

These rules guide AI coding agents working on this Spring Boot project. Organize the application as **Layered Architecture + Package by Feature**: keep the familiar N-layer flow (controller → service → repository) *inside each feature* instead of global `controller/`, `service/`, `repository/` packages, so those global layer directories do not grow unbounded. This is one deployable application. A feature package is an organizational boundary for finding and maintaining related code; creating one does not require DDD, bounded contexts, separate builds, messaging, or formal Modular Monolith rules.

Apply these rules when creating or modifying code. In case of conflict, prioritize: (1) explicit user instructions, (2) these rules, (3) existing project conventions where compatible. Preserve working behavior and avoid unrelated refactoring. Use the project's actual root package, Java/Spring versions, naming, and build tools. The feature names below are examples, not required features.

## 2. Feature First, Then Layer

Place code by the use case or business feature it serves, then by its technical responsibility inside that feature:

```text
<root-package>/
├── Application.java
├── common/                    shared technical concerns only
│   └── storage/               file storage port and provider adapters
├── auth/
│   ├── controller/
│   ├── service/
│   │   ├── AuthService.java
│   │   └── implement/
│   │       └── AuthServiceImpl.java
│   ├── dto/{request,response}/
│   ├── captcha/                 support packages used by auth
│   └── entity/  repository/  mapper/  exception/
├── otp/                        shared OTP generation and verification
│   ├── service/implement/
│   └── model/  exception/
├── notification/
│   └── external/               email and SMS delivery contracts and implementations
│       ├── service/implement/
│       └── dto/  exception/
├── verification/
│   └── ekyc/                   shared identity verification provider port and adapters
├── user/
│   ├── entity/  repository/  dto/
│   └── service/
└── vehicle/
    ├── controller/  dto/  entity/  repository/
    └── service/
```

Before placing a class, answer:

1. Which existing feature owns its behavior and data? Prefer that package.
2. What layer does it belong to within that feature?
3. Is a new feature package actually useful for finding and maintaining related code?

Start with reasonably broad features such as `auth`, `user`, `vehicle`, and `booking`. Split an oversized feature into subfeatures only when the code and workflows make that useful; a subfeature can have its own `controller/`, `service/`, `service/implement/`, `repository/`, `entity/`, and `dto/` where needed. Helper packages inside a feature (for example `auth/captcha/`) are support components of that feature, not automatically standalone features. Shared identity verification provider integration belongs in `verification/ekyc/`; the provider-neutral file storage port and adapters belong in `common/storage/`. Do not duplicate the same ownership in two places.

Do not create empty layers for symmetry. Do not organize the whole application into global `controller/`, `service/`, `repository/`, and `entity/` packages. `common/` is reserved for genuinely shared technical concerns, not misplaced business logic. Do not move files solely to make the tree look uniform.

## 3. Layer Responsibilities

- `controller/`: HTTP routing, request validation, response mapping. Delegate business work to service interfaces.
- `service/`: application/business service **interfaces** defining cohesive operations.
- `service/implement/`: **all concrete implementations of application/business services**, regardless of whether a service is high-level or low-level, public or internal, and regardless of the number of implementations. Business rules, use-case orchestration, and transaction boundaries live here.
- `repository/`: persistence and queries for the feature. Repositories do not own use-case workflows.
- `entity/`: persistence state and focused entity behavior; do not put HTTP handling or full use-case workflows here.
- `dto/`: request, response, and service-boundary data. Split into `request/` and `response/` when the volume and direction justify it.
- `mapper/`, `exception/`, `config/`: create only if the feature needs them.
- Provider client/SDK code: keep it in a descriptive package within the owning feature, such as `verification/ekyc/` or `auth/captcha/`. Add a port interface and adapter package if isolating a real external dependency improves clarity or testing; do not create them mechanically.

Do not expose JPA entities as REST responses by default. Keep credentials, provider-specific types, and persistence details out of request/response DTOs and service contracts.

## 4. Mandatory Service Interface and Implementation Rule

Every application/business service MUST have an interface in the nearest owning feature's `service/` and its implementation in that same service package's **`implement/`** child package. This applies to lower-level services and services with only one implementation.

```text
<feature>/service/<Name>Service.java
<feature>/service/implement/<Name>ServiceImpl.java
```

When a feature has subfeatures, use the subfeature as `<feature>`. Do not place concrete service classes directly in `service/`, in a global implementation directory, or in `impl/`. The directory name for this project is exactly `implement`.

- `<Name>Service` is an interface. `<Name>ServiceImpl` implements it and carries `@Service` when registered as a Spring bean.
- Controllers and other services declare constructor dependencies using service interfaces, not implementation classes. Keep service dependencies final; do not use field injection or instantiate Spring services manually.
- Put `@Transactional` on appropriate implementation methods/classes. Check proxy behavior and self-invocation when changing transaction boundaries.
- Keep interfaces cohesive. Do not expose private implementation helpers as public methods merely to satisfy this rule.
- When multiple beans implement one service interface, make Spring selection explicit.
- Tests may instantiate implementations directly as subjects under test; production consumers inject interfaces.
- This policy concerns application/business services. It does not require interface/implementation pairs for entities, DTOs, controllers, mapper classes, configuration, exceptions, or ordinary private helpers. A technical client may implement a provider port inside its integration package without being renamed `*ServiceImpl`.

When modifying an existing service still using `impl/` or a concrete class in `service/`, migrate that service and affected imports/consumers together. Do not rewrite every unrelated feature just to standardize the repository unless the user requests that migration. Report remaining inconsistencies accurately.

## 5. Feature Collaboration and Ownership

A feature may call another feature's service interface when a use case needs it. These are maintainability guidelines for a single deployable application, not strict module boundaries: keep calls clear and avoid dependency cycles. Prefer a small DTO or result specific to the caller's needs; do not make one feature reach into another feature's repository, implementation, or mutable entity. If a cycle appears, inspect which feature owns the workflow before adding a shared abstraction.

A third-party integration is used by a feature, not automatically a new feature. OTP creation, expiry, cooldown, attempt limits, and verification belong to the shared `otp` feature. Business features such as `auth` call `otp.service.OtpService`; OTP passes only recipient and OTP data to `notification.external.service.ExternalNotificationDispatcherService`. Channel-specific implementations create their own content and deliver it through focused capability interfaces such as `OtpExternalNotificationService`. The dispatcher routes each operation to the configured external channel. Keep `otp` independent of `auth` and retain business workflows in their owning features. External delivery exceptions belong to `notification/external/exception/`. Add a sibling realtime notification package only when realtime delivery is implemented. `auth` owns the account activation workflow and calls the shared eKYC provider port in `verification/ekyc/`. Future driver license workflows should own their own approval rules and use verification capabilities as needed. Base placement on the actual code and requirements, not a generic example.

Only introduce events, queues, retry infrastructure, or a new architectural boundary when current requirements justify them. Preserve transaction and failure behavior during refactoring.

## 6. No Hard-Coded Values

Do not hard-code environment-dependent, secret, provider-specific, or changeable business values in Java source. Examples include URLs, credentials, API keys, file paths, OTP length/expiry/attempt limits, thresholds, fees, timeouts, email sender addresses, and provider identifiers when they are configurable.

- Use the project's configuration mechanism for deployable values: application properties/YAML, environment variables, and typed `@ConfigurationProperties` where appropriate. Never commit real secrets; supply safe placeholders/examples as needed.
- Use well-named constants or enums for stable code-level values such as protocol-defined codes, fixed labels, and domain states. If business staff must change a value without a code release, put it in configuration or the existing database-driven settings mechanism instead.
- Avoid unexplained magic numbers and repeated string literals. Use named values with one clear source of truth.
- Framework-required literals, mapping paths, format tokens, and simple self-explanatory values may remain inline when extracting them would reduce clarity. This rule is about configurable or meaningful values, not banning every literal in Java.
- Do not invent new properties or database settings merely to remove a literal; inspect existing configuration and actual variability first.

## 7. No Comments in Code

Do not add inline comments, block comments, JavaDoc, TODO/FIXME notes, or commented-out code to Java and other source code you create or modify. Express intent with clear names, small methods, and cohesive structure. Document public behavior in the project's existing documentation when necessary. Keep license headers or comments required by generated code or external tooling if the project legally or technically requires them; do not remove unrelated existing comments as part of a focused edit.

This restriction applies to source code, not this Markdown rules document or other requested documentation.

## 8. SOLID and Simplicity

- **SRP:** A class should have a cohesive reason to change; separate HTTP, business, persistence, and provider concerns.
- **OCP:** Add a variation point when there is actual variation; avoid speculative factories and strategies.
- **LSP:** Implementations honor the service/port contract, including errors and behavior.
- **ISP:** Keep interfaces focused on operations their consumers use.
- **DIP:** Consumers depend on service interfaces; business services use ports for external providers where that boundary is useful. A feature's Spring Data repository interface remains an appropriate dependency for its service implementation.


An interface alone does not prove SOLID compliance. Prefer readable code and the smallest change that satisfies the task.

## 9. Workflow and Verification

Before editing:

1. Inspect the relevant feature packages, callers, persistence, configuration, and tests.
2. Identify the correct feature, layer, existing patterns, and behavior to preserve.
3. For a substantial feature move, check affected transactions, dependencies, persisted data, and API compatibility. Explain the planned moves briefly, then carry out the authorized work.

After editing:

1. Check package declarations, imports, Spring bean resolution, constructor injection, and service interface/`service/implement/` pairs.
2. Check changed code for hard-coded values, new source comments, cross-feature repository/implementation access, and dependency cycles.
3. Run the relevant build and tests when application code changes. Add focused tests for changed behavior when needed. Do not claim checks passed unless actually run.
4. Report changed files, behavior, checks, and any remaining limits. Documentation-only changes need a content review; they do not require an application build.

Do not make broad file moves or speculative infrastructure changes during a focused task.
