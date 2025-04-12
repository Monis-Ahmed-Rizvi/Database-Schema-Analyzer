package com.schema.analyzer.controller;

import com.schema.analyzer.model.AnalysisResult;
import com.schema.analyzer.service.NormalizationService;
import com.schema.analyzer.service.SchemaAnalysisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * REST controller for schema analysis.
 */
@RestController
@RequestMapping("/schemas")
public class SchemaController {

    private final NormalizationService normalizationService;

    @Autowired
    public SchemaController(NormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    /**
     * Test endpoint to verify connectivity.
     */
    @GetMapping("/test")
    public ResponseEntity<String> testConnection() {
        return ResponseEntity.ok("API is working!");
    }

    /**
     * Analyzes a SQL schema provided as a string.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeSchema(@RequestBody @Valid SchemaRequest request) {
        try {
            AnalysisResult result = normalizationService.analyzeSchema(request.getSqlScript());
            return ResponseEntity.ok(result);
        } catch (SchemaAnalysisException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Analyzes a SQL schema provided as a file upload.
     */
    @PostMapping("/analyze-file")
    public ResponseEntity<?> analyzeSchemaFile(@RequestParam("file") MultipartFile file) {
        try {
            // Read file content
            String sqlScript = readFileContent(file);
            
            // Analyze the schema
            AnalysisResult result = normalizationService.analyzeSchema(sqlScript);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Error reading file: " + e.getMessage()));
        } catch (SchemaAnalysisException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Generates SQL statements to improve a schema based on an analysis result.
     */
    @PostMapping("/generate-improvement")
    public ResponseEntity<?> generateImprovement(@RequestBody @Valid SchemaRequest request) {
        try {
            // First, analyze the schema
            AnalysisResult result = normalizationService.analyzeSchema(request.getSqlScript());
            
            // Then, generate improvement SQL
            String improvementSql = normalizationService.generateImprovementSql(result);
            
            return ResponseEntity.ok(new ImprovementResponse(improvementSql));
        } catch (SchemaAnalysisException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Reads content from a multipart file upload.
     */
    private String readFileContent(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}