package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ObuFirmwareStorageLayoutTest {
    @ParameterizedTest
    @ValueSource(strings = { "ota", "OTA", " ota " })
    void reservesTheOtaRootIgnoringCase(String name) {
        assertThat(ObuFirmwareStorageLayout.isReservedName(name)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "Commsignia", "otas", "ota/x" })
    void doesNotReserveOtherNames(String name) {
        assertThat(ObuFirmwareStorageLayout.isReservedName(name)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "ota/Commsignia/a.tar.sig,true", "OTA/a,true", "ota,true", "otab/a,false",
            "Commsignia/ota/a,false", "'',false" })
    void recognizesObjectsBeneathTheRoot(String objectName, boolean expected) {
        assertThat(ObuFirmwareStorageLayout.isObuObjectName(objectName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "ota/Commsignia/a.tar.sig,Commsignia", "OTA/Commsignia/a.tar.sig,Commsignia",
            "ota/firmwares/a.tar.sig,firmwares", "ota/a.tar.sig,", "ota/Commsignia/nested/a.tar.sig,",
            "ota/Commsignia/,", "ota//a.tar.sig,", "Commsignia/ITS/a.tar.sig," })
    void findsTheManufacturerFolderOfUploadPaths(String objectName, String expected) {
        assertThat(ObuFirmwareStorageLayout.manufacturerFolder(objectName)).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void buildsObjectNamesBeneathTheRoot() {
        assertThat(ObuFirmwareStorageLayout.objectName("Commsignia", "a.tar.sig"))
                .isEqualTo("ota/Commsignia/a.tar.sig");
        assertThat(ObuFirmwareStorageLayout.isObuObjectName(null)).isFalse();
    }
}
