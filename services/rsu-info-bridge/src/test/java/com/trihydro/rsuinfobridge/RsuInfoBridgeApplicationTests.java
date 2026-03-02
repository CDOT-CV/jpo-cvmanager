package com.trihydro.rsuinfobridge;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class RsuInfoBridgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
