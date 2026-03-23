package com.trihydro.rsuinfobridge;

import com.trihydro.rsuinfobridge.testutil.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ImportTestcontainers(IntegrationTestContainers.class)
class RsuInfoBridgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
