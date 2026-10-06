package com.hrm.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AttendanceProperties.class)
public class AttendanceConfig {

    @Bean
    public Clock attendanceClock(AttendanceProperties properties) {
        return Clock.system(properties.companyZoneId());
    }
}
