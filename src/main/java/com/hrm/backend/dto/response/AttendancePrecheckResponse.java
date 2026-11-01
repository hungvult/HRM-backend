package com.hrm.backend.dto.response;

import java.time.OffsetDateTime;

// Chỉ mô tả bằng chứng đạt tại thời điểm kiểm tra; không phải quyền ghi công cho lần bấm tiếp theo.
public record AttendancePrecheckResponse(boolean eligible, String verificationMode,
        boolean wifiVerified, boolean gpsVerified, OffsetDateTime verifiedAt) {
}
