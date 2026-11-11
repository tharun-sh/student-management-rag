# RAG Implementation Plan (Spring AI + Google Gemini Free Tier)

How the chat endpoint answers questions about students and courses. See [`PROJECT_PLAN.md`](./PROJECT_PLAN.md) for the overall project.

---

## 1. How It Works (simple version)

```
                ┌──────────── WRITE PATH (CRUD) ────────────┐
 POST/PUT/DELETE│                                           │
 ──────────────►│ StudentService ──► H2 (real data)         │
                │        │ after commit                      │
                │        ▼                                   │
                │ VectorSyncService ──► DocumentFactory      │
                │        │   (row → text paragraph)          │
                │        ▼                                   │
                │ Gemini embedding ──► SimpleVectorStore     │
                └───────────────────────────────────────────┘

                ┌──────────── READ PATH (CHAT) ─────────────┐
 POST /chat     │ question ──► Gemini embedding              │
 ──────────────►│        │                                   │
                │        ▼                                   │
                │ SimpleVectorStore: find top-K similar docs │
                │        │                                   │
                │        ▼                                   │
                │ prompt = question + matching docs          │
                │        │                                   │
                │        ▼                                   │
                │ gemini-2.5-flash ──► answer                │
                └───────────────────────────────────────────┘
```

1. Each Student and Course row is turned into a short **text paragraph** (a `Document`).
2. Gemini turns each paragraph into an **embedding** (a list of numbers that captures its meaning).
3. Embeddings are kept in **SimpleVectorStore** (in memory).
4. When a question comes in, it's embedded too, and the most similar paragraphs are found.
5. Those paragraphs plus the question are sent to **gemini-2.5-flash**, which answers **using only that context**.

---

## 2. Decisions

| Topic | Decision |
|---|---|
| Chat model | `gemini-2.5-flash` (free tier) |
| Embedding model | `gemini-embedding-001` |
| Spring AI integration | `spring-ai-starter-model-google-genai` + `-embedding` (Gemini Developer API with an API key, no GCP project needed) |
| Vector store | `SimpleVectorStore` (in memory) |
| Documents | One per **Student**, one per **Course** |
| Sync | Auto-sync after every CRUD commit **+** `POST /api/rag/reindex` **+** full rebuild on startup |
| API key | `GEMINI_API_KEY` in `~/.bashrc`, read via `${GEMINI_API_KEY}` |
| RAG wiring | `ChatClient` + `QuestionAnswerAdvisor` |
| Spring AI version | 2.0.2 |
| Sync execution | Synchronous after commit (no `@Async` in v1) |
| Refresh logic | Only in `VectorSyncService`; CRUD services just publish `EntityChangedEvent` |
| Aggregates (counts/averages) | Known v1 limitation; tool calling deferred to v2 |

---

## 3. API Key Setup (`~/.bashrc`)

1. Create a free key at **Google AI Studio** → <https://aistudio.google.com/apikey>.
2. Add it to `~/.bashrc`:
   ```bash
   echo 'export GEMINI_API_KEY="your-key-here"' >> ~/.bashrc
   source ~/.bashrc
   ```
3. Verify it's set: `echo ${GEMINI_API_KEY:0:6}…` (prints only the first few characters).
4. **Never** commit the key. It lives only in `~/.bashrc`, and `application.properties` refers to it via `${GEMINI_API_KEY}`.

**Gotchas**
- `~/.bashrc` is read by **interactive terminals**. Run `./mvnw spring-boot:run` from a terminal where it's loaded.
- An **IDE launched from the desktop menu** (IntelliJ, VS Code) may not see variables from `~/.bashrc`. Either start the IDE from a terminal (`idea .` / `code .`) or set `GEMINI_API_KEY` in the IDE's run configuration.
- If the variable is missing, the app should **fail fast at startup** with a clear message (see §4).

---

## 4. Configuration

### Dependencies (in addition to PROJECT_PLAN §2)
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai-embedding</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
```
`SimpleVectorStore` ships with Spring AI core (`spring-ai-vector-store`, pulled in transitively), so no extra dependency is needed.

### `application.properties`
```properties
# --- Gemini chat ---
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.chat.options.model=gemini-2.5-flash
spring.ai.google.genai.chat.options.temperature=0.2

# --- Gemini embeddings ---
spring.ai.google.genai.embedding.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.embedding.text.options.model=gemini-embedding-001

# --- App RAG settings ---
app.rag.top-k=5
app.rag.similarity-threshold=0.5
app.rag.reindex-on-startup=true
```
> Check the exact property names against the Spring AI reference docs for the version you use (*Google GenAI Chat* and *Google GenAI Embeddings* pages). Property names have changed between Spring AI milestones.

**Fail fast:** with `${GEMINI_API_KEY}` and no default, startup fails if the variable isn't set. A small `@PostConstruct` check in `RagConfig` can print a friendlier message: *"GEMINI_API_KEY not set. Add it to ~/.bashrc and run `source ~/.bashrc`."*

---

## 5. Documents (`DocumentFactory`)

Converts entities into Spring AI `Document`s. The text is what gets embedded, so write it as **natural sentences** that contain every searchable fact.

### Student document
```
Student: Ravi Kumar (ID 1)
Email: ravi.kumar@example.com
Department: Computer Science
Year of study: 3
GPA: 3.8
Date of birth: 2004-05-12
Enrolled courses: CS201 Data Structures, MA101 Calculus
```
Metadata: `{ "type": "student", "entityId": 1, "department": "Computer Science", "yearOfStudy": 3, "gpa": 3.8 }`

### Course document
```
Course: CS201 Data Structures (ID 2)
Credits: 4
Instructor: Dr. Meena
Description: Arrays, linked lists, trees, graphs, ...
Enrolled students (3): Ravi Kumar, Anita Rao, John Paul
```
Metadata: `{ "type": "course", "entityId": 2, "code": "CS201" }`

### Deterministic document IDs
So an update **replaces** the old embedding instead of adding a duplicate:
```java
String id = UUID.nameUUIDFromBytes(("student-" + studentId).getBytes(UTF_8)).toString();
String id = UUID.nameUUIDFromBytes(("course-"  + courseId ).getBytes(UTF_8)).toString();
```
(UUID format keeps the code portable if you later switch to PGVector or Chroma.)

---

## 6. Sync Strategy (`VectorSyncService`)

All three mechanisms use the same two methods:

```java
void upsertStudent(Long studentId);  // reload from DB → build doc → delete(id) → add(doc)
void upsertCourse(Long courseId);
void removeStudent(Long studentId);  // delete(id)
void removeCourse(Long courseId);
void reindexAll();                   // clear store → load all → batch add
```

### 6.1 Auto-sync on CRUD
Which documents need refreshing after each operation:

| Operation | Refresh |
|---|---|
| Create / update student | that student |
| Delete student | remove student doc + refresh each course they were in |
| Create / update course | that course + every enrolled student (their text lists the course title) |
| Delete course | remove course doc + refresh previously enrolled students |
| Enroll / unenroll | that student + that course |

**When it runs:** after the DB transaction **commits**, never inside it:
- The service publishes `EntityChangedEvent(type, id, action, relatedIds)`. For deletes, `relatedIds` carries the affected student or course IDs, since they can't be loaded after the delete.
- `VectorSyncService` handles it with `@TransactionalEventListener(phase = AFTER_COMMIT)` and owns **all** of the "what to refresh" logic in the table above. The CRUD services stay unaware of RAG.
- **Synchronous** in v1, with no `@Async`: the response waits about 0.5s for Gemini, which keeps it simple to debug.
- The listener reloads entities in a new read-only transaction (`@Transactional(propagation = REQUIRES_NEW, readOnly = true)`).

**Failure handling:** if the Gemini embedding call fails (network error or 429 rate limit), **log a warning and carry on**. The CRUD request still succeeds. The data is safe in H2 and is fixed by the next reindex.

### 6.2 Manual reindex — `POST /api/rag/reindex`
- Clears the store (delete all known IDs; SimpleVectorStore has no `clear()`, so track IDs or recreate the store's contents).
- Loads all students and courses (fetch-join the many-to-many to avoid N+1 queries).
- Builds all documents and calls `vectorStore.add(docs)` in **batches** (e.g. 20 docs per batch) to stay under free-tier rate limits.
- Returns `{ "studentsIndexed": 10, "coursesIndexed": 5, "durationMs": 2310 }`.
- Guard with a simple lock (`AtomicBoolean`) so two reindexes can't run at once. Return 409 if one is already running.
- **Accepted v1 race:** a CRUD write that lands during a reindex may be overwritten by the reindex. The next write or reindex fixes it, so it's ignored.

### 6.3 Rebuild on startup
SimpleVectorStore is **in memory**, so it's empty after every restart, while H2 (file-based) still has the data.
- An `ApplicationRunner` (`@Order(2)`, after the seeder) calls `reindexAll()` when `app.rag.reindex-on-startup=true`.
- *Optional optimization:* save the store to `./data/vector-store.json` with `simpleVectorStore.save(file)` on shutdown and `load(file)` on startup, then skip the rebuild. Not needed for a small dataset, so leave it out of v1.

---

## 7. Beans (`RagConfig`)

```java
@Configuration
class RagConfig {

    @Bean
    SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore,
                          @Value("${app.rag.top-k}") int topK,
                          @Value("${app.rag.similarity-threshold}") double threshold) {
        return builder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .build())
                .build())
            .build();
    }
}
```

### System prompt
```
You are an assistant for a student management system.
Answer ONLY using the student and course records provided in the context.
If the context does not contain the answer, say "I couldn't find that in the student records."
Do not invent students, courses, grades or numbers.
Be concise. When listing students, include their name and the relevant fields.
```

---

## 8. Chat Endpoint

### `POST /api/rag/chat`
Request (`ChatRequest`, validated):
```json
{ "question": "Which Computer Science students have a GPA above 3.5?" }
```
- `question`: `@NotBlank`, `@Size(max = 500)`.
- Optional `"type": "student" | "course"` to filter retrieval by metadata, e.g. `filterExpression("type == 'student'")`.

Response (`ChatResponse`):
```json
{
  "answer": "Two Computer Science students have a GPA above 3.5: Ravi Kumar (3.8) and Anita Rao (3.6).",
  "sources": [
    { "type": "student", "entityId": 1, "score": 0.82 },
    { "type": "student", "entityId": 4, "score": 0.79 }
  ]
}
```
`sources` comes from the retrieved documents' metadata (read them from the `ChatClientResponse` context, `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`). This lets you see **which records** the answer was based on, which helps with debugging.

### `ChatService`
```java
ChatClientResponse r = chatClient.prompt().user(question).call().chatClientResponse();
String answer = r.chatResponse().getResult().getOutput().getText();
List<Document> docs = (List<Document>) r.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
```

### `GET /api/rag/status`
```json
{ "documentCount": 15, "lastReindexAt": "2026-11-10T14:00:00Z", "chatModel": "gemini-2.5-flash", "embeddingModel": "gemini-embedding-001" }
```

---

## 9. Known Limitations (worth knowing as a beginner)

- **Counting and aggregate questions** ("How many students are there?", "What's the average GPA?") are weak spots: RAG only sees the top-K documents, not the whole table. **Accepted for v1; tool calling is planned for v2.** A later improvement is Spring AI **tool calling**, where the model calls a `countStudents()` / `averageGpa()` Java method that queries the repository.
- **Free-tier rate limits:** Gemini's free tier caps requests per minute and per day (check current limits in AI Studio). Batching during reindex, plus logging and continuing on 429s, keeps you safe with a small dataset.
- **Free-tier data use:** Google may use free-tier prompts to improve its models. Only use **fake sample data**, never real student information.
- **In-memory store:** rebuilt on every startup, which is fine for tens to hundreds of records.

---

## 10. Implementation Steps

1. Get the API key and add it to `~/.bashrc` (§3). Check with `echo`.
2. Add the Spring AI BOM and the three dependencies; `./mvnw clean compile`.
3. Add the Gemini properties (§4). Smoke test: a temporary `/api/rag/ping` that calls `chatClient.prompt().user("Say hi").call().content()`.
4. `DocumentFactory`: entity → `Document` with deterministic IDs (§5).
5. `RagConfig`: `SimpleVectorStore` + `ChatClient` with `QuestionAnswerAdvisor` (§7).
6. `VectorSyncService`: upsert/remove/reindexAll with batching and a lock (§6).
7. Startup `ApplicationRunner` for the rebuild (§6.3).
8. `EntityChangedEvent`, published from `StudentService` / `CourseService`, plus the `@TransactionalEventListener(AFTER_COMMIT)` handler (§6.1).
9. `ChatService` + `RagController` (`/chat`, `/reindex`, `/status`) (§8).
10. Map Gemini errors to 503 (`ProblemDetail`) in `GlobalExceptionHandler`.
11. **Tests:** the `test` profile uses a dummy key and `reindex-on-startup=false`; `VectorSyncService` and `ChatService` are `@MockitoBean`s (PROJECT_PLAN §11). No test calls Gemini.
12. **Manual verification:**
    - Ask: *"Who is in CS201?"* and check the answer.
    - Update a student's GPA via `PUT`, ask again, and check the new GPA appears (auto-sync works).
    - Delete a student, ask about them, and check they're gone.
    - Restart the app and ask again; the answer should be correct (startup rebuild works).
    - Call `POST /api/rag/reindex` and check the counts match the DB.

---

## 11. Sample Questions to Try

- "List all students in the Computer Science department."
- "Which students have a GPA above 3.5?"
- "Who teaches Data Structures and which students are enrolled?"
- "What courses is Ravi Kumar taking?"
- "Which third-year students are enrolled in Calculus?"
- "Tell me about the course CS101."
