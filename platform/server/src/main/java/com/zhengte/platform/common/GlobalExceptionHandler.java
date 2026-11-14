/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.dao.DuplicateKeyException
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.RestControllerAdvice
 */
package com.zhengte.platform.common;

import com.zhengte.platform.common.ApiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value={ApiException.class})
    public ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return ResponseEntity.status((HttpStatusCode)e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(value={DuplicateKeyException.class})
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status((int)409).body(Map.of("message", "\u6570\u636e\u5df2\u5b58\u5728\u6216\u5f53\u524d\u8d44\u6e90\u88ab\u5360\u7528"));
    }

    @ExceptionHandler(value={Exception.class})
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        log.error("Unhandled error", (Throwable)e);
        return ResponseEntity.status((int)500).body(Map.of("message", "\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef"));
    }
}
