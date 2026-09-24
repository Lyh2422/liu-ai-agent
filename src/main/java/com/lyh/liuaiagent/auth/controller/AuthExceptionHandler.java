package com.lyh.liuaiagent.auth.controller;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, Object>> badCredentials(BadCredentialsException exception) {
        return body(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException exception) {
        return body(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException exception) {
        return body(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException exception) {
        return body(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({IOException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String, Object>> uploadFailure(Exception exception) {
        return body(HttpStatus.BAD_REQUEST, exception.getMessage() == null ? "文件处理失败" : exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "请求参数不合法");
        response.put("fields", fields);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> fallback(Exception exception, HttpServletRequest request) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器处理失败");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
