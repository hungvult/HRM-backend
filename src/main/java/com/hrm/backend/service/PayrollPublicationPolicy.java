package com.hrm.backend.service;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.PayrollRun;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import com.hrm.backend.exception.AuthException;
import java.time.OffsetDateTime;

/** Applies the only allowed payroll publication transition without recalculating a snapshot. */
public final class PayrollPublicationPolicy {
    private PayrollPublicationPolicy() { }

    public static boolean publish(PayrollRun run, Account actor, OffsetDateTime now) {
        if (run.getStatus() == PayrollRunStatus.PUBLISHED) return false;
        if (run.getStatus() != PayrollRunStatus.GENERATED) {
            throw new AuthException("PAYROLL_RUN_STATE_CONFLICT", "Bảng lương không ở trạng thái có thể công bố.", 409);
        }
        run.setStatus(PayrollRunStatus.PUBLISHED);
        run.setPublishedByAccount(actor);
        run.setPublishedAt(now);
        return true;
    }
}
