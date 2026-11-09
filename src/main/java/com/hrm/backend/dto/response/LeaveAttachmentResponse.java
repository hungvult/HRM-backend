package com.hrm.backend.dto.response;

public record LeaveAttachmentResponse(String fileName, String contentType, long sizeBytes, String url) { }
