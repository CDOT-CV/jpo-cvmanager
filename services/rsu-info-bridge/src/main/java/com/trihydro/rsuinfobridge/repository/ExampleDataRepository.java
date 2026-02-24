package com.trihydro.rsuinfobridge.repository;

import com.trihydro.rsuinfobridge.models.dtos.RsuDto;

import java.util.List;

public class ExampleDataRepository {

    final static String AUTHENTICATION_PROTOCOL = "SHA";
    final static String PRIVACY_PROTOCOL = "AES";

    public static List<RsuDto> getData() {
        List<RsuDto> rsus = new java.util.ArrayList<>();

        RsuDto rsu1 = RsuDto.builder()
                .id("myid")
                .ipv4Address("10.10.10.10")
                .snmpProtocol("NTCIP1218")
                .snmpUsername("myusername")
                .snmpPassword("mypassword")
                .authenticationProtocol(AUTHENTICATION_PROTOCOL)
                .privacyProtocol(PRIVACY_PROTOCOL)
                .latitude(39.73915)
                .longitude(-104.9847)
                .timDepositEnabled(true)
                .build();
        rsus.add(rsu1);

        RsuDto rsu2 = RsuDto.builder()
                .id("myid2")
                .ipv4Address("10.10.10.11")
                .snmpProtocol("NTCIP1218")
                .snmpUsername("myusername2")
                .snmpPassword("mypassword2")
                .authenticationProtocol(AUTHENTICATION_PROTOCOL)
                .privacyProtocol(PRIVACY_PROTOCOL)
                .latitude(40.0)
                .longitude(105.0)
                .timDepositEnabled(true)
                .build();
        rsus.add(rsu2);

        return rsus;
    }
}
