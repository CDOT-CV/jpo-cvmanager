package us.dot.its.jpo.ode.api.mappers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@Import(ScmsHealthMapperImpl.class)
class ScmsHealthMapperTest {

    @Autowired
    private ScmsHealthMapper scmsHealthMapper;

    @Nested
    @DisplayName("Tests for toDto mapper method")
    class ToDtoTests {
        @Test
        void testToDto_Success() {
            ScmsHealth scmsHealth = new ScmsHealth();
            scmsHealth.setId(1);
            scmsHealth.setTimestamp(Instant.now());
            scmsHealth.setHealth(true);
            scmsHealth.setExpiration(Instant.now().plusSeconds(3600));

            Rsu rsu = new Rsu();
            rsu.setId(10);
            scmsHealth.setRsu(rsu);

            ScmsHealthDto result = scmsHealthMapper.toDto(scmsHealth);

            assertNotNull(result);
            assertEquals(scmsHealth.getId(), result.getId());
            assertEquals(scmsHealth.getTimestamp(), result.getTimestamp());
            assertEquals(scmsHealth.getHealth(), result.isHealth());
            assertEquals(scmsHealth.getExpiration(), result.getExpiration());
            assertEquals(scmsHealth.getRsu().getId(), result.getRsuId());
        }

        @Test
        void testToDto_Null() {
            ScmsHealthDto result = scmsHealthMapper.toDto(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Tests for toDtoList mapper method")
    class ToDtoListTests {
        @Test
        void testToDtoList_Success() {
            List<ScmsHealth> scmsHealthList = new ArrayList<>();
            
            ScmsHealth scmsHealth1 = new ScmsHealth();
            scmsHealth1.setId(1);
            scmsHealth1.setTimestamp(Instant.now());
            scmsHealth1.setHealth(true);
            scmsHealth1.setExpiration(Instant.now().plusSeconds(3600));
            Rsu rsu1 = new Rsu();
            rsu1.setId(10);
            scmsHealth1.setRsu(rsu1);
            
            ScmsHealth scmsHealth2 = new ScmsHealth();
            scmsHealth2.setId(2);
            scmsHealth2.setTimestamp(Instant.now().minusSeconds(60));
            scmsHealth2.setHealth(false);
            scmsHealth2.setExpiration(Instant.now().plusSeconds(7200));
            Rsu rsu2 = new Rsu();
            rsu2.setId(20);
            scmsHealth2.setRsu(rsu2);

            scmsHealthList.add(scmsHealth1);
            scmsHealthList.add(scmsHealth2);

            List<ScmsHealthDto> result = scmsHealthMapper.toDtoList(scmsHealthList);

            assertNotNull(result);
            assertEquals(2, result.size());
            
            assertEquals(scmsHealth1.getId(), result.get(0).getId());
            assertEquals(scmsHealth1.getRsu().getId(), result.get(0).getRsuId());
            
            assertEquals(scmsHealth2.getId(), result.get(1).getId());
            assertEquals(scmsHealth2.getRsu().getId(), result.get(1).getRsuId());
        }

        @Test
        void testToDtoList_Empty() {
            List<ScmsHealthDto> result = scmsHealthMapper.toDtoList(new ArrayList<>());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void testToDtoList_Null() {
            List<ScmsHealthDto> result = scmsHealthMapper.toDtoList(null);
            assertNull(result);
        }
    }
}
