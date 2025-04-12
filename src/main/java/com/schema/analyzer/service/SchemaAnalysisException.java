package com.schema.analyzer.service;

/**
 * Exception thrown when schema analysis fails.
 */
public class SchemaAnalysisException extends RuntimeException {
    
    public SchemaAnalysisException(String message) {
        super(message);
    }
    
    public SchemaAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
