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

    /** Hai lượt lưu có thể cùng vượt qua kiểm trùng; ràng buộc DB quyết định lượt sau và trả 409 thay vì 500. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraint = "";
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation && violation.getConstraintName() != null)
                constraint = violation.getConstraintName().toLowerCase(java.util.Locale.ROOT);
        }
        String code = "DATA_CONFLICT";
        String message = "Dữ liệu đã thay đổi hoặc đang được sử dụng. Vui lòng tải lại và kiểm tra trước khi lưu.";
        if (constraint.contains("employee") && constraint.contains("code")) { code="EMPLOYEE_CODE_ALREADY_EXISTS";message="Mã nhân viên đã tồn tại."; }
        else if (constraint.contains("department") && constraint.contains("code")) { code="DEPARTMENT_CODE_ALREADY_EXISTS";message="Mã phòng ban đã tồn tại."; }
        else if (constraint.contains("position") && constraint.contains("code")) { code="POSITION_CODE_ALREADY_EXISTS";message="Mã chức vụ đã tồn tại."; }
        return handleAuthException(new AuthException(code, message, 409), request);
    }

    // Trả đúng tên trường của hợp đồng API, không đưa tên constraint hay câu SQL ra giao diện.
    private String conflictField(String code) {
        return switch (code) {
            case "EMPLOYEE_CODE_ALREADY_EXISTS" -> "employeeCode";
            case "DEPARTMENT_CODE_ALREADY_EXISTS", "POSITION_CODE_ALREADY_EXISTS" -> "code";
            case "USERNAME_ALREADY_EXISTS" -> "username";
            case "EMAIL_ALREADY_EXISTS", "EMPLOYEE_EMAIL_ALREADY_EXISTS" -> "email";
            default -> null;
        };
    }

    // Multipart typed request yêu cầu JSON; part sai MIME trả 415 để client sửa, không biến thành lỗi hệ thống.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMedia(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return handleAuthException(new AuthException("UNSUPPORTED_MEDIA_TYPE", "Kiểu dữ liệu không được hỗ trợ; part request phải là application/json.", 415), request);
    }

    // Spring có thể chặn multipart trước controller; giữ cùng mã lỗi dung lượng với bộ kiểm tra ảnh của service.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return handleAuthException(new AuthException("LEAVE_ATTACHMENT_TOO_LARGE", "Ảnh tối đa 5 MB; toàn bộ yêu cầu tối đa 6 MB.", 413), request);
    }

    // Thiếu JSON part request hoặc multipart sai cấu trúc phải là lỗi đầu vào, không trả 500.
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMultipart(Exception ex, HttpServletRequest request) {
        return handleAuthException(new AuthException("INVALID_REQUEST_BODY", "Dữ liệu multipart không hợp lệ hoặc thiếu part request JSON.", 400), request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(ex.getStatus())
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(getTraceId(request))
                .errors(ex.getErrors() != null ? ex.getErrors() : conflictField(ex.getCode()) == null ? null : List.of(
                        ErrorResponse.FieldError.builder().field(conflictField(ex.getCode())).message(ex.getMessage()).build()))
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
                        || "locationCapturedAt".equals(field)
                        || "gpsGroupComplete".equals(field));
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
