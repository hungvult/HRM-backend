package com.hrm.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PayrollViewControllerSecurityTest {
    @Test
    void hrRunEndpointsAndPersonalPayslipEndpointsHaveDifferentAccessScopes() throws NoSuchMethodException {
        Method run = PayrollViewController.class.getDeclaredMethod("getRun", Long.class);
        Method runs = PayrollViewController.class.getDeclaredMethod("getRuns", Short.class, Short.class,
                com.hrm.backend.entity.enums.PayrollRunStatus.class, Short.class, Short.class, Short.class,
                Short.class, int.class, int.class);
        Method items = PayrollViewController.class.getDeclaredMethod("getRunItems", Long.class, String.class,
                Long.class, int.class, int.class);
        Method mine = PayrollViewController.class.getDeclaredMethod("getMyPayslips",
                com.hrm.backend.security.CustomUserDetails.class, Short.class, int.class, int.class);

        assertAuthorization(runs, "hasRole('HR')");
        assertAuthorization(run, "hasRole('HR')");
        assertAuthorization(items, "hasRole('HR')");
        assertAuthorization(mine, "isAuthenticated()");
    }

    private void assertAuthorization(Method method, String expected) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals(expected, authorization.value());
    }
}
