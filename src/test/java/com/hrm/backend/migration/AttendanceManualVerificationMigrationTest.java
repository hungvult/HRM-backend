package com.hrm.backend.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AttendanceManualVerificationMigrationTest {
    @Test
    void realPostgresSeparatesAuditedCorrectionsFromSelfServiceEvidence() throws Exception {
        String url = System.getenv("DB_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Requires PostgreSQL (provided by Backend CI)");
        try (Connection db = DriverManager.getConnection(url,
                System.getenv().getOrDefault("DB_USERNAME", "postgres"),
                System.getenv().getOrDefault("DB_PASSWORD", "postgres"))) {
            db.setAutoCommit(false);
            try {
                execute(db, """
                    CREATE TEMP TABLE attendance_records (
                        id BIGINT PRIMARY KEY,
                        is_manually_adjusted BOOLEAN NOT NULL DEFAULT false,
                        adjusted_by_account_id BIGINT,
                        adjustment_reason TEXT,
                        check_in_at TIMESTAMPTZ,
                        check_out_at TIMESTAMPTZ,
                        check_in_wifi_ssid TEXT,
                        check_out_wifi_ssid TEXT,
                        check_in_wifi_network_id BIGINT,
                        check_out_wifi_network_id BIGINT,
                        check_in_work_location_id BIGINT,
                        check_out_work_location_id BIGINT,
                        check_in_latitude NUMERIC,
                        check_out_latitude NUMERIC,
                        check_in_longitude NUMERIC,
                        check_out_longitude NUMERIC,
                        check_in_location_distance_meters INTEGER,
                        check_out_location_distance_meters INTEGER,
                        check_in_gps_accuracy_meters NUMERIC,
                        check_out_gps_accuracy_meters NUMERIC,
                        check_in_location_captured_at TIMESTAMPTZ,
                        check_out_location_captured_at TIMESTAMPTZ
                    ) ON COMMIT DROP
                    """);
                // Preserve an existing historical row without fabricated evidence.
                execute(db, "INSERT INTO pg_temp.attendance_records(id,check_in_at) VALUES (1,now())");
                String migration;
                try (var input = getClass().getResourceAsStream("/db/migration/R__attendance_manual_verification.sql")) {
                    assertNotNull(input);
                    migration = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("public.attendance_records", "pg_temp.attendance_records");
                }
                execute(db, migration);
                execute(db, migration); // Repeatable migrations must be safe to run again.
                try (var rows = db.createStatement().executeQuery("SELECT count(*) FROM pg_temp.attendance_records WHERE id=1")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }

                execute(db, """
                    INSERT INTO pg_temp.attendance_records
                    (id,is_manually_adjusted,adjusted_by_account_id,adjustment_reason,check_in_at,check_out_at)
                    VALUES (2,true,10,'Correction approved by HR',now()-interval '8 hours',now())
                    """);
                execute(db, "UPDATE pg_temp.attendance_records SET is_manually_adjusted=true, adjusted_by_account_id=10, adjustment_reason='Fix historical time' WHERE id=1");
                execute(db, "INSERT INTO pg_temp.attendance_records(id) VALUES (3)");

                rejected(db, "INSERT INTO pg_temp.attendance_records(id,check_in_at) VALUES (4,now())");
                rejected(db, "INSERT INTO pg_temp.attendance_records(id,check_out_at) VALUES (5,now())");
                rejected(db, "INSERT INTO pg_temp.attendance_records(id,is_manually_adjusted,adjustment_reason,check_in_at) VALUES (6,true,'Missing actor',now())");
                rejected(db, "INSERT INTO pg_temp.attendance_records(id,is_manually_adjusted,adjusted_by_account_id,check_out_at) VALUES (7,true,10,now())");
                rejected(db, "INSERT INTO pg_temp.attendance_records(id,is_manually_adjusted,adjusted_by_account_id,adjustment_reason,check_in_at) VALUES (8,true,10,'   ',now())");
                rejected(db, "INSERT INTO pg_temp.attendance_records(id,is_manually_adjusted,adjusted_by_account_id,adjustment_reason,check_in_at) VALUES (9,false,10,'An actor alone cannot bypass GPS',now())");
                rejected(db, "UPDATE pg_temp.attendance_records SET adjustment_reason='' WHERE id=2");
                rejected(db, "UPDATE pg_temp.attendance_records SET is_manually_adjusted=false WHERE id=2");

                execute(db, """
                    INSERT INTO pg_temp.attendance_records
                    (id,check_in_at,check_in_wifi_ssid,check_in_wifi_network_id,check_in_work_location_id,
                     check_in_latitude,check_in_longitude,check_in_location_distance_meters,
                     check_in_gps_accuracy_meters,check_in_location_captured_at)
                    VALUES (20,now(),'QA-WIFI',2,1,10.776889,106.700806,0,5,now())
                    """);
                execute(db, """
                    UPDATE pg_temp.attendance_records SET check_out_at=now(),check_out_wifi_ssid='QA-WIFI',
                    check_out_wifi_network_id=2,check_out_work_location_id=1,check_out_latitude=10.776889,
                    check_out_longitude=106.700806,check_out_location_distance_meters=0,
                    check_out_gps_accuracy_meters=5,check_out_location_captured_at=now() WHERE id=20
                    """);
                for (String side : new String[]{"in", "out"}) {
                    for (String field : new String[]{"wifi_network_id", "work_location_id", "latitude", "longitude",
                            "location_distance_meters", "gps_accuracy_meters", "location_captured_at"}) {
                        rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_" + field + "=NULL WHERE id=20");
                    }
                    rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_wifi_ssid=' ' WHERE id=20");
                    rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_latitude=91 WHERE id=20");
                    rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_longitude=181 WHERE id=20");
                    rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_gps_accuracy_meters=0 WHERE id=20");
                    rejected(db, "UPDATE pg_temp.attendance_records SET check_" + side + "_location_distance_meters=-1 WHERE id=20");
                }
            } finally {
                db.rollback();
            }
        }
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void rejected(Connection db, String sql) throws SQLException {
        Savepoint before = db.setSavepoint();
        try {
            SQLException error = assertThrows(SQLException.class, () -> execute(db, sql));
            assertEquals("23514", error.getSQLState(), "Must reject because verification/audit evidence is missing");
        } finally {
            db.rollback(before);
            db.releaseSavepoint(before);
        }
    }
}
