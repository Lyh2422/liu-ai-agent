package com.lyh.liuaiagent.knowledge;

import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.Map;

@Order(-1)
@RestControllerAdvice(assignableTypes = KnowledgeController.class)
public class KnowledgeExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    ResponseEntity<?> invalid(Exception error) { return ResponseEntity.badRequest().body(Map.of("message", "请求不合法：" + (error instanceof IllegalArgumentException ? error.getMessage() : "请检查文件和请求参数"))); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<?> conflict(Exception error) { return ResponseEntity.status(409).body(Map.of("message", "文档已被其他管理员修改，请重新加载")); }
    @ExceptionHandler(KnowledgeManagementService.IndexUpdateException.class)
    ResponseEntity<?> unavailable(Exception error) { return ResponseEntity.status(503).body(Map.of("message", error.getMessage())); }
}
