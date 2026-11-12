package us.dot.its.jpo.ode.api.accessorTests.counts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.accessors.counts.MessageTypeParams;

public class MessageTypeParamsTest {

    @Test
    void parsesCommaSeparatedTypes() {
        assertThat(MessageTypeParams.parse("BSM,MAP,SPAT")).containsExactly("BSM", "MAP", "SPAT");
    }

    @Test
    void parsesRepeatedParametersAndDedupes() {
        assertThat(MessageTypeParams.parse(List.of("BSM", "MAP", "bsm"))).containsExactly("BSM", "MAP");
    }

    @Test
    void parsesMixedRepeatedAndCommaSeparatedValues() {
        assertThat(MessageTypeParams.parse(List.of("BSM,MAP", "SPAT"))).containsExactly("BSM", "MAP", "SPAT");
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(MessageTypeParams.parse((String) null)).isEmpty();
        assertThat(MessageTypeParams.parse("")).isEmpty();
        assertThat(MessageTypeParams.parse((List<String>) null)).isEmpty();
        assertThat(MessageTypeParams.parse(List.of())).isEmpty();
    }
}
