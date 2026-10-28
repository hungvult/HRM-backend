package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.entity.enums.AttendanceStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Specifications for management attendance searches. */
public final class AttendanceRecordSpecifications {
    private AttendanceRecordSpecifications() {
    }

    public static Specification<AttendanceRecord> matchesSearch(String q, Long employeeId, Long departmentId,
                                                                  AttendanceStatus status, LocalDate fromDate,
                                                                  LocalDate toDate, Long managerEmployeeId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<AttendanceRecord, Employee> employee = root.join("employee");

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(employee.get("employeeCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(employee.get("fullName")), pattern)));
            }
            if (employeeId != null) {
                predicates.add(criteriaBuilder.equal(employee.get("id"), employeeId));
            }
            if (departmentId != null) {
                predicates.add(effectiveAssignmentExists(root, query, criteriaBuilder, "department", departmentId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("attendanceStatus"), status));
            }
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("workDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("workDate"), toDate));
            }
            if (managerEmployeeId != null) {
                predicates.add(effectiveAssignmentExists(root, query, criteriaBuilder,
                        "managerEmployee", managerEmployeeId));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate effectiveAssignmentExists(Root<AttendanceRecord> attendance,
                                                        CriteriaQuery<?> query,
                                                        CriteriaBuilder criteriaBuilder,
                                                        String assignmentAssociation,
                                                        Long scopeId) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<EmployeeAssignment> assignment = subquery.from(EmployeeAssignment.class);
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(assignment.get("employee"), attendance.get("employee")),
                criteriaBuilder.equal(assignment.get(assignmentAssociation).get("id"), scopeId),
                criteriaBuilder.lessThanOrEqualTo(assignment.get("effectiveFrom"), attendance.get("workDate")),
                criteriaBuilder.or(
                        criteriaBuilder.isNull(assignment.get("effectiveTo")),
                        criteriaBuilder.greaterThanOrEqualTo(assignment.get("effectiveTo"), attendance.get("workDate"))));
        return criteriaBuilder.exists(subquery);
    }

}
