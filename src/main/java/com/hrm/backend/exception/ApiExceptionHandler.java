package com.hrm.backend.exception;

import com.hrm.backend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(ex.getStatus())
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .errors(ex.getErrors())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.NOT_FOUND.value())
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        boolean invalidAttendanceGps = isAttendanceGpsValidationError(request.getRequestURI(), errors);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(invalidAttendanceGps ? "ATTENDANCE_INVALID_GPS" : "VALIDATION_ERROR")
                .message(invalidAttendanceGps ? "Tọa độ GPS không hợp lệ." : "Dữ liệu gửi lên không hợp lệ.")
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .errors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    private boolean isAttendanceGpsValidationError(String path, List<ErrorResponse.FieldError> errors) {
        return isAttendanceEndpoint(path) && errors.stream()
                .map(ErrorResponse.FieldError::getField)
                .anyMatch(field -> "latitude".equals(field)
                        || "longitude".equals(field)
                        || "accuracyMeters".equals(field)
                        || "locationCapturedAt".equals(field));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        boolean invalidAttendanceGps = isAttendanceGpsReadError(request.getRequestURI(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(invalidAttendanceGps ? "ATTENDANCE_INVALID_GPS" : "INVALID_REQUEST_BODY")
                .message(invalidAttendanceGps
                        ? "Tọa độ GPS không hợp lệ."
                        : "Request body không hợp lệ hoặc chứa trường không được hỗ trợ.")
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return badRequest(request, ex.getName(), "Giá trị không đúng kiểu hoặc không thuộc tập giá trị cho phép.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return badRequest(request, ex.getParameterName(), "Tham số bắt buộc không được để trống.");
    }

    private ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request, String field, String fieldMessage) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .code("VALIDATION_ERROR")
                .message("Dữ liệu gửi lên không hợp lệ.")
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .errors(List.of(ErrorResponse.FieldError.builder().field(field).message(fieldMessage).build()))
                .build();
        return ResponseEntity.badRequest().body(errorResponse);
    }

    private boolean isAttendanceGpsReadError(String path, HttpMessageNotReadableException ex) {
        return isAttendanceEndpoint(path) && ex.getMostSpecificCause() instanceof InvalidFormatException;
    }

    private boolean isAttendanceEndpoint(String path) {
        return "/v1/attendance/check-in".equals(path)
                || "/v1/attendance/check-out".equals(path)
                || "/api/v1/attendance/check-in".equals(path)
                || "/api/v1/attendance/check-out".equals(path);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                       HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.FORBIDDEN.value())
                .code("AUTH_FORBIDDEN")
                .message("Bạn không có quyền truy cập tài nguyên này.")
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred.")
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .build();
        // Log the exception securely here without returning stack trace to client
        ex.printStackTrace(); 
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private String getTraceId(HttpServletRequest request) {
        // Fallback traceId if not already set by a tracing filter
        String traceId = (String) request.getAttribute("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute("traceId", traceId);
        }
        return traceId;
    }
}
