package com.hrm.backend.entity.enums;

public enum PayrollRunStatus {
    /** Legacy rows may still contain this value; the MVP never creates or exposes it. */
    @Deprecated
    DRAFT,

    GENERATED,
    PUBLISHED,

    /** Legacy rows may still contain this value; the MVP never creates or exposes it. */
    @Deprecated
    CANCELLED
}
