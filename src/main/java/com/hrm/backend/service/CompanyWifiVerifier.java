package com.hrm.backend.service;

import jakarta.servlet.http.HttpServletRequest;

public interface CompanyWifiVerifier {
    VerifiedCompanyWifi verify(HttpServletRequest request);
}
