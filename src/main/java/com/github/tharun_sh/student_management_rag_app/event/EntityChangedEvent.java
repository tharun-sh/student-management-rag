package com.github.tharun_sh.student_management_rag_app.event;

import java.util.Set;

public record EntityChangedEvent(Type type, Long id, Action action, Set<Long> relatedIds) {
    public enum Type { STUDENT, COURSE }
    public enum Action { CREATED, UPDATED, DELETED, ENROLLMENT_CHANGED }
}
