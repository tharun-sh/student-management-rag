package com.github.tharun_sh.student_management_rag_app.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rebuilds the in-memory vector store after the seeder has run. */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.rag.reindex-on-startup", havingValue = "true", matchIfMissing = true)
public class RagStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagStartupRunner.class);

    private final VectorSyncService sync;

    public RagStartupRunner(VectorSyncService sync) {
        this.sync = sync;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Startup reindex done: {}", sync.reindexAll());
        } catch (Exception e) {
            log.warn("Startup reindex failed (call POST /api/rag/reindex later): {}", e.getMessage());
        }
    }
}
