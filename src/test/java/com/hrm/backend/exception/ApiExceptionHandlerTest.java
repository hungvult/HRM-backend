package com.hrm.backend.exception;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.databind.exc.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsAttendanceInvalidGps_whenCheckInLatitudeIsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/attendance/check-in");

        var response = handler.handleValidationException(validationException("latitude", "Vĩ độ là bắt buộc."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ATTENDANCE_INVALID_GPS", response.getBody().getCode());
        assertEquals("Tọa độ GPS không hợp lệ.", response.getBody().getMessage());
    }

    @Test
    void returnsAttendanceInvalidGps_whenCheckOutLongitudeIsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/attendance/check-out");

        var response = handler.handleValidationException(validationException("longitude", "Kinh độ là bắt buộc."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ATTENDANCE_INVALID_GPS", response.getBody().getCode());
        assertEquals("Tọa độ GPS không hợp lệ.", response.getBody().getMessage());
    }

    @Test
    void keepsValidationError_forNonAttendanceRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/departments");

        var response = handler.handleValidationException(validationException("latitude", "Vĩ độ là bắt buộc."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getCode());
    }

    @Test
    void returnsAttendanceInvalidGps_whenCheckInLatitudeHasWrongFormat() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/attendance/check-in");
        var invalidFormat = new InvalidFormatException(
                null, "Không thể chuyển latitude sang số.", "abc", java.math.BigDecimal.class);
        var exception = new HttpMessageNotReadableException(
                "Request body không hợp lệ.", invalidFormat, mock(HttpInputMessage.class));

        var response = handler.handleMalformedJson(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ATTENDANCE_INVALID_GPS", response.getBody().getCode());
        assertEquals("Tọa độ GPS không hợp lệ.", response.getBody().getMessage());
    }

    private MethodArgumentNotValidException validationException(String field, String message) {
        var bindingResult = new BeanPropertyBindingResult(new AttendanceLocationRequest(null, null, null, null), "request");
        bindingResult.addError(new FieldError("request", field, message));
        return new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
    }
}
