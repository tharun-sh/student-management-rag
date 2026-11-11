package com.github.tharun_sh.student_management_rag_app.exception;

public class ReindexInProgressException extends RuntimeException {
    public ReindexInProgressException() { super("A reindex is already running"); }
}
