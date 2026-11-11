# Student Management RAG App — Project Plan

A simple Spring Boot backend that manages **Students** and **Courses** (CRUD over REST, stored in H2) and has a **RAG chat endpoint** that answers natural-language questions about the stored records using Google Gemini.

> The RAG part (Gemini setup, embeddings, vector store, sync) is planned in detail in [`RAG_PLAN.md`](./RAG_PLAN.md). This file covers the overall project.

---

## 1. Decisions

| Topic | Decision |
|---|---|
| Framework | Spring Boot 4.1.2, Java 25, Maven (already scaffolded) |
| Base package | `com.github.tharun_sh.student_management_rag_app` |
| Modules | Spring Web (WebMVC), Spring Data JPA, Spring AI, Bean Validation |
| Database | H2, **file-based** (`./data/studentdb`), so data survives restarts |
| Domain | `Student` ↔ `Course`, **many-to-many** |
| RAG source | Student and Course DB records |
| Vector store | Spring AI `SimpleVectorStore` (in memory) |
| Sync | **Auto-sync on every CRUD write** + **manual reindex endpoint** + rebuild on startup |
| LLM | Gemini free tier: `gemini-2.5-flash` (chat), `gemini-embedding-001` (embeddings) |
| API key | `GEMINI_API_KEY` exported in `~/.bashrc` |
| Extras | Bean Validation + global error handler, sample seed data, Swagger UI |
| Spring AI version | 2.0.2 (stable). If incompatible with Boot 4.1.2, downgrade Boot to 4.0.x; don't use an RC |
| Updates | `PUT` only (no `PATCH`) |
| Lists | No pagination; plain lists |
| Course delete | Automatically unenrolls students |
| Errors | Spring `ProblemDetail` (RFC 9457) |
| Tests | Minimal: one `@SpringBootTest` smoke test + `@WebMvcTest` for `StudentController` |
| Out of scope | Auth/security, frontend, pagination, PATCH, Docker, tool calling (v2) |

---

## 2. Dependencies (`pom.xml`)

Add to the existing pom:

```xml
<properties>
    <java.version>25</java.version>
    <spring-ai.version>2.0.2</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-webmvc` | REST API *(already present)* |
| `spring-boot-starter-data-jpa` | JPA / Hibernate *(already present)* |
| `spring-boot-starter-validation` | `@Valid`, `@NotBlank`, `@Email`, … |
| `com.h2database:h2` (runtime) | Database |
| `spring-boot-h2console` | H2 web console (Boot 4 split it into its own module) |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.x` | Swagger UI |
| `spring-ai-starter-model-google-genai` | Gemini chat model |
| `spring-ai-starter-model-google-genai-embedding` | Gemini embedding model |
| `spring-ai-advisors-vector-store` | `QuestionAnswerAdvisor` (RAG advisor) |

> **Version check:** Use Spring AI 2.0.2. If it's incompatible with Boot 4.1.2, **downgrade Boot to 4.0.x** (don't use a Spring AI RC). Use springdoc 3.x, the line for Boot 4.

---

## 3. Package Structure

```
com.github.tharun_sh.student_management_rag_app
├── StudentManagementRagAppApplication.java
├── config/
│   ├── OpenApiConfig.java          # Swagger title/description
│   └── RagConfig.java              # VectorStore + ChatClient beans (see RAG_PLAN)
├── entity/
│   ├── Student.java
│   └── Course.java
├── repository/
│   ├── StudentRepository.java
│   └── CourseRepository.java
├── dto/
│   ├── StudentRequest.java         # record, validated input
│   ├── StudentResponse.java        # record, output (with course summaries)
│   ├── CourseRequest.java
│   ├── CourseResponse.java
│   ├── ChatRequest.java            # (RAG)
│   └── ChatResponse.java           # (RAG)
├── mapper/
│   └── EntityMapper.java           # entity <-> DTO, manual (no MapStruct)
├── service/
│   ├── StudentService.java
│   ├── CourseService.java
│   └── rag/                        # (RAG — see RAG_PLAN)
│       ├── DocumentFactory.java
│       ├── VectorSyncService.java
│       └── ChatService.java
├── controller/
│   ├── StudentController.java
│   ├── CourseController.java
│   └── RagController.java          # (RAG)
├── event/
│   └── EntityChangedEvent.java     # record(type, id, action, relatedIds)
└── exception/
    ├── ResourceNotFoundException.java
    ├── DuplicateResourceException.java
    └── GlobalExceptionHandler.java
```

---

## 4. Domain Model

### `Student`
| Field | Type | Constraints |
|---|---|---|
| `id` | `Long` | PK, generated (IDENTITY) |
| `firstName` | `String` | not blank, max 50 |
| `lastName` | `String` | not blank, max 50 |
| `email` | `String` | not blank, valid email, **unique** |
| `department` | `String` | not blank (e.g. "Computer Science") |
| `yearOfStudy` | `Integer` | 1–5 |
| `gpa` | `Double` | 0.0–4.0 |
| `dateOfBirth` | `LocalDate` | past date, optional |
| `courses` | `Set<Course>` | `@ManyToMany`, owning side |

### `Course`
| Field | Type | Constraints |
|---|---|---|
| `id` | `Long` | PK, generated |
| `code` | `String` | not blank, **unique** (e.g. "CS101") |
| `title` | `String` | not blank |
| `description` | `String` | optional, max 1000 |
| `credits` | `Integer` | 1–10 |
| `instructor` | `String` | optional |
| `students` | `Set<Student>` | `@ManyToMany(mappedBy = "courses")` |

### Relationship notes
- Join table `student_course(student_id, course_id)`, owned by `Student`.
- Use `Set` (not `List`) and `FetchType.LAZY`. Keep `equals/hashCode` based on `id` only (or don't override them) to avoid Hibernate issues.
- Helper methods on `Student`: `addCourse(Course)` / `removeCourse(Course)` keep both sides in sync.
- When **deleting a Course**, first remove it from every enrolled student's `courses` set (otherwise the delete hits a FK violation).
- DTOs never expose entities directly, which avoids infinite JSON recursion and lazy-loading errors.

---

## 5. REST API

Base path: `/api`

### Students — `/api/students`
| Method | Path | Description | Success |
|---|---|---|---|
| `GET` | `/api/students` | List all, no pagination (optional `?department=`) | 200 |
| `GET` | `/api/students/{id}` | Get one (with enrolled courses) | 200 |
| `POST` | `/api/students` | Create | 201 + `Location` header |
| `PUT` | `/api/students/{id}` | Full update | 200 |
| `DELETE` | `/api/students/{id}` | Delete | 204 |
| `POST` | `/api/students/{id}/courses/{courseId}` | Enroll in course | 200 |
| `DELETE` | `/api/students/{id}/courses/{courseId}` | Unenroll from course | 200 |

### Courses — `/api/courses`
| Method | Path | Description | Success |
|---|---|---|---|
| `GET` | `/api/courses` | List all | 200 |
| `GET` | `/api/courses/{id}` | Get one (with enrolled students) | 200 |
| `POST` | `/api/courses` | Create | 201 |
| `PUT` | `/api/courses/{id}` | Full update | 200 |
| `DELETE` | `/api/courses/{id}` | Delete (unenrolls students first) | 204 |
| `GET` | `/api/courses/{id}/students` | List enrolled students | 200 |

### RAG — `/api/rag` *(details in RAG_PLAN.md)*
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/rag/chat` | Ask a question about students/courses |
| `POST` | `/api/rag/reindex` | Rebuild the vector store from H2 |
| `GET` | `/api/rag/status` | Number of indexed documents, last reindex time |

### Example payloads

`POST /api/students`
```json
{
  "firstName": "Ravi",
  "lastName": "Kumar",
  "email": "ravi.kumar@example.com",
  "department": "Computer Science",
  "yearOfStudy": 3,
  "gpa": 3.8,
  "dateOfBirth": "2004-05-12"
}
```

`StudentResponse`
```json
{
  "id": 1,
  "firstName": "Ravi",
  "lastName": "Kumar",
  "email": "ravi.kumar@example.com",
  "department": "Computer Science",
  "yearOfStudy": 3,
  "gpa": 3.8,
  "dateOfBirth": "2004-05-12",
  "courses": [ { "id": 2, "code": "CS201", "title": "Data Structures" } ]
}
```

---

## 6. Service Layer

- `@Service` classes, constructor injection, `@Transactional` on writes and `@Transactional(readOnly = true)` on reads.
- Business rules:
  - Duplicate `email` (student) or `code` (course) → `DuplicateResourceException` (409).
  - Missing id → `ResourceNotFoundException` (404).
  - Enrolling a student who's already enrolled is a no-op (idempotent).
- **RAG hook:** services know nothing about RAG. After every write they only publish an `EntityChangedEvent` (with `ApplicationEventPublisher`). `VectorSyncService` listens **after commit** and decides which documents to refresh (RAG_PLAN §6). A rollback never leaves a stale embedding behind, and a Gemini failure never rolls back the DB write.
- For deletes, the event carries the **related IDs** (e.g. the course IDs a deleted student was in), because they can't be loaded after the commit.

---

## 7. Validation & Error Handling

- DTOs are Java `record`s annotated with `@NotBlank`, `@Email`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Past`.
- Controllers use `@Valid @RequestBody`.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps:

| Exception | Status |
|---|---|
| `MethodArgumentNotValidException` | 400 (with per-field errors) |
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` / `DataIntegrityViolationException` | 409 |
| RAG / Gemini errors (e.g. rate limit) | 503 |
| Anything else | 500 |

Errors use Spring's built-in **`ProblemDetail`** (RFC 9457). Validation errors add an `errors` property:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/students",
  "errors": { "email": "must be a well-formed email address" }
}
```

---

## 8. Configuration (`application.properties`)

```properties
spring.application.name=student_management_rag_app

# --- H2 (file-based) ---
spring.datasource.url=jdbc:h2:file:./data/studentdb;AUTO_SERVER=TRUE
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# --- JPA ---
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.open-in-view=false

# --- Seed data (only inserts when tables are empty, see §9) ---
spring.sql.init.mode=never

# --- Swagger ---
springdoc.swagger-ui.path=/swagger-ui.html

# --- Gemini / RAG: see RAG_PLAN.md §4 ---
```

Add `data/` to `.gitignore`.

---

## 9. Seed Data

Because H2 is file-based, a plain `data.sql` would re-insert rows on every start. Use a `CommandLineRunner` (`DataSeeder`) instead:

- If `studentRepository.count() == 0`, insert **5 courses** (e.g. CS101 Intro to Programming, CS201 Data Structures, MA101 Calculus, PH101 Physics, EC101 Economics) and **10 students** across 3–4 departments, with varied GPAs, years, and enrollments.
- Runs **before** the vector-store startup rebuild (`@Order(1)` for the seeder, `@Order(2)` for the RAG rebuild), so the first rebuild already includes the seed data.

---

## 10. Swagger / OpenAPI

- springdoc auto-generates docs from the controllers.
- `OpenApiConfig` sets the title "Student Management RAG API" and a short description.
- Add `@Operation(summary = ...)` on the controller methods.
- UI: `http://localhost:8080/swagger-ui.html`

---

## 11. Tests (minimal)

| Test | What it checks |
|---|---|
| `StudentManagementRagAppApplicationTests` (`@SpringBootTest`) | Context loads, so the wiring is OK |
| `StudentControllerTest` (`@WebMvcTest(StudentController.class)`) | 201 on create, 400 + `errors` on invalid input, 404 on a missing id; `StudentService` is a `@MockitoBean` |

**`test` profile** (`src/test/resources/application-test.properties`, tests use `@ActiveProfiles("test")`):
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.ai.google.genai.api-key=dummy
spring.ai.google.genai.embedding.api-key=dummy
app.rag.reindex-on-startup=false
```
`VectorSyncService` and `ChatService` are replaced with `@MockitoBean`s, so **no test calls Gemini** and no real key is needed.

---

## 12. Implementation Order

1. **Dependencies:** update `pom.xml`; `./mvnw clean compile`.
2. **Config:** H2 + JPA properties; verify `/h2-console` works.
3. **Entities + repositories:** `Student`, `Course`, many-to-many mapping.
4. **DTOs + mapper.**
5. **Exceptions + `GlobalExceptionHandler`.**
6. **Services:** Student, Course, enrollment logic.
7. **Controllers:** Student, Course.
8. **Seed data:** `DataSeeder`.
9. **Swagger:** verify all endpoints show up in the UI.
10. **Manual CRUD test:** via Swagger/curl.
11. **RAG:** follow `RAG_PLAN.md`.
12. **Tests:** smoke test + `StudentControllerTest`; `./mvnw test`.
13. **End-to-end check:** create/update/delete a student, then ask the chat endpoint and confirm the answer reflects the change.

---

## 13. Running

```bash
source ~/.bashrc            # makes GEMINI_API_KEY available (see RAG_PLAN §3)
./mvnw spring-boot:run
```

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Try the API |
| `http://localhost:8080/h2-console` | Browse the DB (JDBC URL `jdbc:h2:file:./data/studentdb`) |
