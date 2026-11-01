package com.hrm.backend.filter;

import com.hrm.backend.entity.ApiLog;
import com.hrm.backend.repository.ApiLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiLoggingFilterTest {
    @Test void skipsMultipartAndBinaryWhilePreservingResponseBytes() throws Exception {
        var repo = mock(ApiLogRepository.class);
        var filter = new ApiLoggingFilter(repo);
        var request = new MockHttpServletRequest("POST", "/v1/leave-requests");
        request.setContentType("multipart/form-data; boundary=test");
        request.setContent("SECRET_BINARY_UPLOAD".getBytes());
        var response = new MockHttpServletResponse();
        byte[] image = new byte[]{0, 1, 2, (byte) 255};
        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            res.setContentType("image/png"); res.getOutputStream().write(image);
        });
        var captor = ArgumentCaptor.forClass(ApiLog.class); verify(repo).save(captor.capture());
        assertEquals("", captor.getValue().getRequestBody());
        assertEquals("", captor.getValue().getResponseBody());
        assertFalse(captor.getValue().getContent().contains("SECRET_BINARY_UPLOAD"));
        assertArrayEquals(image, response.getContentAsByteArray());
    }

    @Test void retainsJsonBodiesForExistingApis() throws Exception {
        var repo = mock(ApiLogRepository.class);
        var request = new MockHttpServletRequest("POST", "/v1/leave-requests");
        request.setContentType("application/json"); request.setContent("{\"reason\":\"test\"}".getBytes());
        new ApiLoggingFilter(repo).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            req.getInputStream().readAllBytes(); res.setContentType("application/json"); res.getWriter().write("{}");
        });
        var captor = ArgumentCaptor.forClass(ApiLog.class); verify(repo).save(captor.capture());
        assertEquals("{\"reason\":\"test\"}", captor.getValue().getRequestBody());
        assertEquals("{}", captor.getValue().getResponseBody());
    }
}
