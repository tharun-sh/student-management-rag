package com.github.tharun_sh.student_management_rag_app.service.rag;

import com.github.tharun_sh.student_management_rag_app.dto.ReindexResult;
import com.github.tharun_sh.student_management_rag_app.entity.Course;
import com.github.tharun_sh.student_management_rag_app.entity.Student;
import com.github.tharun_sh.student_management_rag_app.event.EntityChangedEvent;
import com.github.tharun_sh.student_management_rag_app.exception.ReindexInProgressException;
import com.github.tharun_sh.student_management_rag_app.repository.CourseRepository;
import com.github.tharun_sh.student_management_rag_app.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class VectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncService.class);

    private final SimpleVectorStore vectorStore;
    private final DocumentFactory documents;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final int batchSize;

    private final Set<String> indexedIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean reindexing = new AtomicBoolean(false);
    private volatile Instant lastReindexAt;

    public VectorSyncService(SimpleVectorStore vectorStore, DocumentFactory documents,
                             StudentRepository students, CourseRepository courses,
                             @Value("${app.rag.batch-size:20}") int batchSize) {
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.students = students;
        this.courses = courses;
        this.batchSize = batchSize;
    }

    // ---- auto-sync after commit ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onEntityChanged(EntityChangedEvent e) {
        try {
            switch (e.type()) {
                case STUDENT -> {
                    switch (e.action()) {
                        case DELETED -> {
                            removeStudent(e.id());
                            e.relatedIds().forEach(this::upsertCourse);
                        }
                        case ENROLLMENT_CHANGED -> {
                            upsertStudent(e.id());
                            e.relatedIds().forEach(this::upsertCourse);
                        }
                        default -> upsertStudent(e.id());
                    }
                }
                case COURSE -> {
                    switch (e.action()) {
                        case DELETED -> {
                            removeCourse(e.id());
                            e.relatedIds().forEach(this::upsertStudent);
                        }
                        default -> {
                            upsertCourse(e.id());
                            e.relatedIds().forEach(this::upsertStudent);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Vector sync failed for {} (data is safe in H2; run POST /api/rag/reindex): {}", e, ex.getMessage());
        }
    }

    // ---- single-document operations ----

    public void upsertStudent(Long id) {
        students.findByIdWithCourses(id).ifPresent(s -> replace(documents.toDocument(s)));
    }

    public void upsertCourse(Long id) {
        courses.findByIdWithStudents(id).ifPresent(c -> replace(documents.toDocument(c)));
    }

    public void removeStudent(Long id) {
        remove(DocumentFactory.studentDocId(id));
    }

    public void removeCourse(Long id) {
        remove(DocumentFactory.courseDocId(id));
    }

    private void replace(Document doc) {
        vectorStore.delete(List.of(doc.getId()));
        vectorStore.add(List.of(doc));
        indexedIds.add(doc.getId());
    }

    private void remove(String docId) {
        vectorStore.delete(List.of(docId));
        indexedIds.remove(docId);
    }

    // ---- full rebuild ----

    @Transactional(readOnly = true)
    public ReindexResult reindexAll() {
        if (!reindexing.compareAndSet(false, true)) {
            throw new ReindexInProgressException();
        }
        long start = System.currentTimeMillis();
        try {
            if (!indexedIds.isEmpty()) {
                vectorStore.delete(new ArrayList<>(indexedIds));
                indexedIds.clear();
            }
            List<Student> allStudents = students.findAllWithCourses();
            List<Course> allCourses = courses.findAllWithStudents();
            List<Document> docs = new ArrayList<>();
            allStudents.forEach(s -> docs.add(documents.toDocument(s)));
            allCourses.forEach(c -> docs.add(documents.toDocument(c)));

            for (int i = 0; i < docs.size(); i += batchSize) {
                List<Document> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
                vectorStore.add(batch);
                batch.forEach(d -> indexedIds.add(d.getId()));
            }
            lastReindexAt = Instant.now();
            return new ReindexResult(allStudents.size(), allCourses.size(), System.currentTimeMillis() - start);
        } finally {
            reindexing.set(false);
        }
    }

    public int documentCount() {
        return indexedIds.size();
    }

    public Instant lastReindexAt() {
        return lastReindexAt;
    }
}
