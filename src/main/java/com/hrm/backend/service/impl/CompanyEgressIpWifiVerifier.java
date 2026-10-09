package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.entity.WorkLocationWifiNetwork;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.WorkLocationWifiNetworkRepository;
import com.hrm.backend.service.CompanyWifiVerifier;
import com.hrm.backend.service.VerifiedCompanyWifi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class CompanyEgressIpWifiVerifier implements CompanyWifiVerifier {

    private final AttendanceProperties properties;
    private final WorkLocationWifiNetworkRepository wifiNetworks;

    @Override
    public VerifiedCompanyWifi verify(HttpServletRequest request) {
        InetAddress sourceAddress = parseSourceAddress(request.getRemoteAddr());
        AttendanceProperties.TrustedWifiSource source = properties.getTrustedSources().stream()
                .filter(candidate -> contains(candidate.getCidr(), sourceAddress))
                .findFirst()
                .orElseThrow(() -> invalidWifi());

        WorkLocationWifiNetwork network = wifiNetworks.findActiveWithLocationById(source.getWifiNetworkId())
                .orElseThrow(() -> invalidWifi());
        return new VerifiedCompanyWifi(network, sourceAddress);
    }

    private InetAddress parseSourceAddress(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException ex) {
            throw invalidWifi();
        }
    }

    private boolean contains(String cidr, InetAddress address) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("CIDR Wi-Fi công ty không hợp lệ.");
        }
        try {
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] candidate = address.getAddress();
            int prefixLength = Integer.parseInt(parts[1]);
            if (network.length != candidate.length || prefixLength < 0 || prefixLength > network.length * 8) {
                return false;
            }
            int completeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            if (!Arrays.equals(Arrays.copyOf(network, completeBytes), Arrays.copyOf(candidate, completeBytes))) {
                return false;
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (network[completeBytes] & mask) == (candidate[completeBytes] & mask);
        } catch (UnknownHostException | NumberFormatException ex) {
            throw new IllegalStateException("CIDR Wi-Fi công ty không hợp lệ.", ex);
        }
    }

    private AuthException invalidWifi() {
        return new AuthException(
                "ATTENDANCE_INVALID_COMPANY_WIFI",
                "Không thể chấm công: thiết bị chưa kết nối Wi-Fi công ty.",
                403);
    }
}
