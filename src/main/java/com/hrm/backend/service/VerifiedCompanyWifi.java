package com.hrm.backend.service;

import com.hrm.backend.entity.WorkLocationWifiNetwork;

import java.net.InetAddress;

public record VerifiedCompanyWifi(WorkLocationWifiNetwork network, InetAddress sourceAddress) {
}
