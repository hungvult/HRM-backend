package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.entity.WorkLocation;
import com.hrm.backend.entity.WorkLocationWifiNetwork;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.WorkLocationWifiNetworkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyEgressIpWifiVerifierTest {

    @Test
    void verify_rejectsForgedCompanyPrefixEvenWhenNginxIsTrusted() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("203.0.113.0/24");
        source.setWifiNetworkId(8L);
        AttendanceProperties.TrustedProxy proxy = new AttendanceProperties.TrustedProxy();
        proxy.setCidr("172.20.0.2/32");
        properties.setTrustedSources(List.of(source));
        properties.setTrustedProxies(List.of(proxy));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.24, 198.51.100.99");
        AuthException exception = assertThrows(AuthException.class,
                () -> new CompanyEgressIpWifiVerifier(properties, mock(WorkLocationWifiNetworkRepository.class)).verify(request));
        assertEquals("ATTENDANCE_INVALID_COMPANY_WIFI", exception.getCode());
    }

    @Test
    void verify_rejectsEmptyForwardedHopInsteadOfTreatingItAsLoopback() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("127.0.0.0/8");
        source.setWifiNetworkId(8L);
        AttendanceProperties.TrustedProxy proxy = new AttendanceProperties.TrustedProxy();
        proxy.setCidr("172.20.0.2/32");
        properties.setTrustedSources(List.of(source));
        properties.setTrustedProxies(List.of(proxy));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.24,");
        assertThrows(AuthException.class,
                () -> new CompanyEgressIpWifiVerifier(properties, mock(WorkLocationWifiNetworkRepository.class)).verify(request));
    }

    @Test
    void verify_usesTcpSourceAddressWhenRequestDoesNotComeFromTrustedProxy() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("203.0.113.0/24");
        source.setWifiNetworkId(8L);
        properties.setTrustedSources(List.of(source));
        WorkLocationWifiNetworkRepository networks = mock(WorkLocationWifiNetworkRepository.class);
        WorkLocationWifiNetwork network = WorkLocationWifiNetwork.builder()
                .id(8L)
                .ssid("HRM-CORP")
                .workLocation(WorkLocation.builder().id(2L).isActive(true).build())
                .isActive(true)
                .build();
        when(networks.findActiveWithLocationById(8L)).thenReturn(Optional.of(network));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.24");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1");

        var verified = new CompanyEgressIpWifiVerifier(properties, networks).verify(request);

        assertEquals(network, verified.network());
        assertEquals("203.0.113.24", verified.sourceAddress().getHostAddress());
    }

    @Test
    void verify_usesForwardedAddressOnlyWhenRemoteAddressIsTrustedProxy() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("203.0.113.0/24");
        source.setWifiNetworkId(8L);
        AttendanceProperties.TrustedProxy proxy = new AttendanceProperties.TrustedProxy();
        proxy.setCidr("172.20.0.2/32");
        properties.setTrustedSources(List.of(source));
        properties.setTrustedProxies(List.of(proxy));

        WorkLocationWifiNetworkRepository networks = mock(WorkLocationWifiNetworkRepository.class);
        WorkLocationWifiNetwork network = WorkLocationWifiNetwork.builder()
                .id(8L)
                .ssid("HRM-CORP")
                .workLocation(WorkLocation.builder().id(2L).isActive(true).build())
                .isActive(true)
                .build();
        when(networks.findActiveWithLocationById(8L)).thenReturn(Optional.of(network));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.24, 172.20.0.2");

        var verified = new CompanyEgressIpWifiVerifier(properties, networks).verify(request);

        assertEquals(network, verified.network());
        assertEquals("203.0.113.24", verified.sourceAddress().getHostAddress());
    }

    @Test
    void verify_rejectsAddressOutsideCompanyWifi() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("203.0.113.0/24");
        source.setWifiNetworkId(8L);
        properties.setTrustedSources(List.of(source));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.99");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.24");

        AuthException exception = assertThrows(AuthException.class,
                () -> new CompanyEgressIpWifiVerifier(properties, mock(WorkLocationWifiNetworkRepository.class)).verify(request));

        assertEquals(403, exception.getStatus());
        assertEquals("ATTENDANCE_INVALID_COMPANY_WIFI", exception.getCode());
    }

    @Test
    void verify_rejectsForgedForwardedAddressFromUntrustedRemoteSource() {
        AttendanceProperties properties = new AttendanceProperties();
        AttendanceProperties.TrustedWifiSource source = new AttendanceProperties.TrustedWifiSource();
        source.setCidr("203.0.113.0/24");
        source.setWifiNetworkId(8L);
        properties.setTrustedSources(List.of(source));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.99");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.24");

        AuthException exception = assertThrows(AuthException.class,
                () -> new CompanyEgressIpWifiVerifier(properties, mock(WorkLocationWifiNetworkRepository.class)).verify(request));

        assertEquals(403, exception.getStatus());
        assertEquals("ATTENDANCE_INVALID_COMPANY_WIFI", exception.getCode());
    }
}
