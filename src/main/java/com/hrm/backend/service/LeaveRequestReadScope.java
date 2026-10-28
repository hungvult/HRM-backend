package com.hrm.backend.service;

/** Visibility information derived exclusively from the authenticated account. */
public record LeaveRequestReadScope(boolean companyWide, boolean canReadDirectReports, Long employeeId) {
}
