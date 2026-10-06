# AGENT_CODE_STRUCTURE.md

## 1. Purpose

This document defines the code organization rules for AI coding agents working on this Spring Boot project.

The project follows:

> **Modular Monolith + N-Layer Architecture**

The agent MUST follow these rules when creating, editing, moving, or refactoring code unless the user explicitly requests a different structure.

This document is intentionally domain-agnostic. Do not assume business modules, entities, workflows, or conventions from another project.

---

## 2. Architecture Overview

The application is:

- One Spring Boot application.
- One deployable application.
- Divided into business modules.
- Each module uses N-Layer structure.
- Modules should have clear ownership and boundaries.

Primary organization rule:

```text
Business Module
    └── Layer
```

Do NOT organize the whole project like this:

```text
controller/
service/
repository/
entity/
dto/
```

Instead, organize by module:

```text
module-a/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/

module-b/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

---

## 3. Base Package

Use the project's existing root package.

Example:

```text
com.example.project
```

The main Spring Boot application class should remain in the root package:

```text
com.example.project.Application
```

Business modules should be direct descendants of the root package.

Example:

```text
com.example.project/
├── Application.java
├── common/
├── modulea/
├── moduleb/
└── modulec/
```

Do not create another unrelated root package unless explicitly required.

---

## 4. Business Modules

A module represents a meaningful business capability or bounded responsibility.

Examples are only illustrative:

```text
auth/
user/
order/
product/
payment/
notification/
```

These are NOT mandatory module names.

The agent MUST determine module ownership from the current project's requirements and existing code.

Do not import a module structure from another project.

---

## 5. Standard Module Structure

A module may use:

```text
<module>/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
└── exception/
```

Example:

```text
<module>/
├── controller/
│   └── ExampleController.java
├── service/
│   ├── ExampleService.java
│   └── ExampleServiceImpl.java
├── repository/
│   └── ExampleRepository.java
├── entity/
│   └── Example.java
├── dto/
│   ├── request/
│   │   └── CreateExampleRequest.java
│   └── response/
│       └── ExampleResponse.java
├── mapper/
│   └── ExampleMapper.java
└── exception/
    └── ExampleNotFoundException.java
```

Not every module must contain every layer.

Do NOT create empty packages only for symmetry.

### 5.1. Splitting `dto/` into `request/` and `response/`

Once a module has more than a handful of DTOs, split them:

```text
<module>/dto/
├── request/     ← inbound: what the client sends
└── response/    ← outbound: what the API returns
```

A DTO that is both sent and received does not belong in either sub-package — keep it directly in
`dto/`. Do not create `dto/` at all for a module that has no DTOs.

---

# 6. Layer Responsibilities

## 6.1 Controller Layer

Location:

```text
<module>/controller/
```

Responsibilities:

- Receive HTTP requests.
- Validate request input.
- Read path variables, query parameters, and request bodies.
- Call application/service logic.
- Return HTTP responses.
- Handle HTTP-specific concerns.

Controllers MUST NOT contain core business rules.

Bad:

```java
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateRequest request) {
    if (request.getAmount() <= 0) {
        // business rule
    }

    // complex business workflow
}
```

Good:

```java
@PostMapping
public ResponseEntity<Response> create(
        @Valid @RequestBody CreateRequest request) {

    return ResponseEntity.ok(service.create(request));
}
```

---

## 6.2 Service Layer

Location:

```text
<module>/service/
```

Responsibilities:

- Business logic.
- Use-case orchestration.
- Business validation.
- Transaction boundaries.
- Calling repositories owned by the same module.
- Calling public services/interfaces of other modules.

The service layer is the main application/business orchestration layer.

Do not move database queries directly into controllers.

---

## 6.3 Repository Layer

Location:

```text
<module>/repository/
```

Responsibilities:

- Persistence.
- Database queries.
- Spring Data JPA repository definitions.
- Query-specific persistence operations.

Repositories MUST NOT own application workflows or business processes.

Good:

```java
public interface ExampleRepository
        extends JpaRepository<Example, Long> {

    Optional<Example> findById(Long id);
}
```

Bad:

```java
public Example processBusinessWorkflow(...) {
    // business logic
}
```

---

## 6.4 Entity Layer

Location:

```text
<module>/entity/
```

Responsibilities:

- Persistence model.
- JPA mappings.
- Entity state.
- Entity behavior when appropriate.

Entities MUST NOT handle:

- HTTP concerns.
- Controller behavior.
- REST response formatting.
- Repository queries.
- Entire application workflows.

Do not expose JPA entities directly as REST API responses unless the project explicitly chooses that design.

---

## 6.5 DTO Layer

Location:

```text
<module>/dto/
<module>/dto/request/
<module>/dto/response/
```

DTOs are used to transfer data between API/application boundaries.

Prefer specific names:

```text
CreateExampleRequest
UpdateExampleRequest
ExampleResponse
ExampleSummaryResponse
```

Avoid vague names when a meaningful name is available:

```text
DataDTO
RequestDTO
ResponseDTO
ExampleDTO
```

Separate request and response models when their responsibilities differ.

---

## 6.6 Mapper Layer

Location:

```text
<module>/mapper/
```

Use mappers when entity/DTO conversion is reused or non-trivial.

Example:

```java
@Mapper(componentModel = "spring")
public interface ExampleMapper {

    ExampleResponse toResponse(Example entity);

    Example toEntity(CreateExampleRequest request);
}
```

Do not create a mapper for every trivial conversion purely to increase abstraction.

Follow the simplest design that keeps the code readable.

---

## 6.7 Exception Layer

Location:

```text
<module>/exception/
```

Use this package for exceptions specific to the module.

Example:

```text
<module>/exception/
├── ExampleNotFoundException.java
└── InvalidExampleStateException.java
```

Cross-cutting/global exception handling may be placed under:

```text
common/exception/
```

---

# 7. Common Package

Location:

```text
common/
```

`common` is only for genuinely shared or cross-cutting functionality.

Examples:

```text
common/
├── config/
├── exception/
├── security/
├── response/
└── util/
```

Possible examples:

```text
common/config/
common/security/
common/exception/
common/response/
```

Do NOT use `common` as a dumping ground.

Bad:

```text
common/
├── SomeModuleService.java
├── SomeModuleRepository.java
├── ModuleSpecificUtils.java
└── BusinessLogic.java
```

Decision rule:

> If the code belongs to one business module, keep it inside that module.

Only move code to `common` when it has a clear, legitimate cross-module purpose.

---

# 8. Module Dependency Rules

## 8.1 Ownership

Every business concept and its persistence logic should have a clear owning module.

A module owns:

```text
its controller
its service
its repository
its entity
its DTOs
its module-specific exceptions
```

Other modules should not bypass that ownership.

---

## 8.2 Cross-Module Access

Do NOT directly access another module's repository.

Bad:

```text
Module A Service
    ↓
Module B Repository
```

Preferred:

```text
Module A Service
    ↓
Module B Public Service/API
    ↓
Module B Repository
```

For example:

```java
moduleBService.findSomething(...);
```

instead of:

```java
moduleBRepository.findSomething(...);
```

This keeps module boundaries explicit.

---

## 8.3 Avoid Circular Dependencies

Avoid:

```text
Module A → Module B → Module A
```

If a circular dependency appears:

1. Identify who owns the use case.
2. Move the workflow to the appropriate owner.
3. Expose a smaller public API if necessary.
4. Extract shared functionality only when it is genuinely shared.
5. Use events only when the project's requirements justify them.

Do NOT solve circular dependencies by randomly moving code into `common`.

---

# 9. Service Interface Rules

Use service interfaces when they provide meaningful abstraction, multiple implementations, or are part of a deliberate module boundary.

Example:

```text
ExampleService.java
ExampleServiceImpl.java
```

Do NOT create interfaces mechanically for every class.

If a concrete service is sufficient for the existing project design, a concrete service is acceptable.

When modifying existing code, follow the project's established convention unless there is a concrete reason to change it.

## 9.1. Service Splitting Checklist

Before adding a new service to a module, ask:

1. **Does it already exist?** Two services with the same shape and one differing parameter are one
   service with an extra argument or an enum, not two classes.
2. **Does it have more than one implementation?** If not, and it is not a deliberate module
   boundary, a concrete `@Service`/`@Component` class is enough — no interface.
3. **Is it a use case or a helper?** Helpers used by one service can be private methods or
   package-private collaborators; only real use cases deserve their own public service.
4. **Is the abstraction earning its keep?** Count the call sites. One call site is usually a sign
   the abstraction is premature.

Iterating over a collection with a lambda (`java.util.function.Supplier`, `Consumer`, …) is
preferred over duplicating a near-identical private method per variant.

---

# 10. Transaction Rules

Transactions should normally be defined at the service/use-case boundary.

Example:

```java
@Service
@Transactional
public class ExampleServiceImpl {
}
```

Do not place application-level transaction boundaries in controllers.

Keep one business operation's transactional behavior understandable and cohesive.

---

# 11. Naming Rules

## Controllers

```text
<Domain>Controller
```

Examples:

```text
ExampleController
AccountController
OrderController
```

## Services

```text
<Domain>Service
<Domain>ServiceImpl
```

Examples:

```text
ExampleService
ExampleServiceImpl
```

## Repositories

```text
<Domain>Repository
```

Examples:

```text
ExampleRepository
AccountRepository
```

## Entities

Use the domain concept name.

Examples:

```text
Example
Account
Order
```

## Request DTOs

Prefer action-specific names:

```text
CreateExampleRequest
UpdateExampleRequest
SearchExampleRequest
```

## Response DTOs

Prefer resource-specific names:

```text
ExampleResponse
ExampleDetailResponse
ExampleSummaryResponse
```

---

# 12. REST API Rules

Prefer resource-oriented REST endpoints.

Example:

```text
GET    /api/examples
GET    /api/examples/{id}
POST   /api/examples
PUT    /api/examples/{id}
DELETE /api/examples/{id}
```

Prefer HTTP methods to express CRUD operations.

Avoid unnecessary action-style paths such as:

```text
POST /api/createExample
POST /api/getExample
POST /api/deleteExample
```

Exception: explicit domain actions may use action endpoints when they represent a meaningful business operation that is not ordinary CRUD.

---

# 13. Configuration Rules

Shared configuration:

```text
common/config/
```

Module-specific configuration stays inside its module when appropriate.

Do not create configuration classes without a concrete purpose.

Examples:

```text
common/config/
├── SecurityConfig.java
└── WebConfig.java
```

These names are illustrative, not mandatory.

---

# 14. Properties and Secrets

Do not commit sensitive credentials.

Recommended structure:

```text
src/main/resources/
├── application.properties
└── application.properties.example
```

The example file may contain placeholders:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/exampledb
spring.datasource.username=your_username
spring.datasource.password=your_password
```

The real local configuration should not contain credentials that are accidentally committed.

Follow the project's actual environment/configuration strategy when one already exists.

---

# 15. File Placement Rules

Before creating a class, the agent MUST determine:

### Step 1: Module ownership

Which business module owns this responsibility?

### Step 2: Layer responsibility

Is it a:

```text
controller
service
repository
entity
dto
mapper
exception
```

or another clearly justified component?

### Step 3: Shared or module-specific?

If it is only used by one module:

```text
<module>/...
```

If it is truly shared:

```text
common/...
```

### Step 4: Dependency direction

Does the new class introduce an unwanted dependency?

Check for:

```text
A → B
B → A
```

before finalizing the design.

---

# 16. Refactoring Rules

When changing existing code, the agent MUST:

1. Preserve the existing architecture unless the user asks for architectural changes.
2. Avoid unrelated file moves.
3. Keep module boundaries clear.
4. Avoid introducing unnecessary abstractions.
5. Reuse existing project conventions.
6. Minimize the scope of the change.
7. Preserve existing public behavior unless the task requires behavior changes.
8. Check imports and package declarations after moving classes.
9. Check module dependencies after refactoring.

Do not refactor the entire project simply because another structure might be cleaner.

---

# 17. Forbidden Patterns

Unless explicitly requested, do NOT introduce:

## 17.1 Global Layer Architecture

```text
controller/
service/
repository/
entity/
```

as the primary whole-project organization.

## 17.2 Cross-Module Repository Access

```text
Module A → Module B Repository
```

## 17.3 Business Logic in Controllers

```text
Controller → complex business workflow
```

## 17.4 Business Workflow in Repositories

```text
Repository → business process
```

## 17.5 Common Package as a Dumping Ground

```text
common/
    everything/
```

## 17.6 Direct Entity Exposure

Do not automatically expose JPA entities as public API models.

## 17.7 Circular Module Dependencies

```text
A → B → A
```

## 17.8 Unnecessary Boilerplate

Do not create abstractions merely for architectural appearance.

Examples:

- interfaces with no meaningful abstraction
- mappers for one trivial assignment
- utility classes for module-specific business logic
- extra layers with no clear responsibility

---

# 18. Example Project Structure

The following is a generic example only.

```text
src/main/java/com/example/project/
│
├── Application.java
│
├── common/
│   ├── config/
│   ├── exception/
│   ├── security/
│   ├── response/
│   └── util/
│
├── modulea/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── mapper/
│   └── exception/
│
├── moduleb/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   └── exception/
│
└── modulec/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

The actual module names MUST come from the current project requirements.

---

# 19. Agent Pre-Change Checklist

Before creating or moving code, verify:

- [ ] I identified the correct business module.
- [ ] I identified the correct layer.
- [ ] I am not creating a global layer package.
- [ ] I am not accessing another module's repository directly.
- [ ] I am not moving module-specific code into `common`.
- [ ] I am not putting business logic in a controller.
- [ ] I am not putting business workflows in a repository.
- [ ] I am not exposing an entity as an API response without a deliberate reason.
- [ ] I am not creating a circular module dependency.
- [ ] I am not adding unnecessary abstraction or boilerplate.
- [ ] I am following the existing project's naming conventions.
- [ ] I am not assuming conventions from another project.

---

# 20. Rule Priority

When rules conflict, use this priority:

1. Explicit user instruction.
2. Existing project architecture and conventions.
3. This document.
4. Agent preference.

The agent MUST NOT change architecture because it personally prefers another architecture.

---

# 21. Core Principle

The goal is not to maximize the number of packages or abstractions.

The goal is:

> **Clear module ownership + clear layer responsibilities + low unnecessary coupling + maintainable code.**

Use the simplest structure that preserves these properties.
