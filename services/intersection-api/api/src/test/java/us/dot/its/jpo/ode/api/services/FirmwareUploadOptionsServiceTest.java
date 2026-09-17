package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;

class FirmwareUploadOptionsServiceTest {

    @Test
    void groupsModelsUnderTheirManufacturersAndExcludesObuFirmware() {
        var repository = mock(RsuModelRepository.class);
        when(repository.findAllWithManufacturerOrdered()).thenReturn(List.of(
                model(1, "Commsignia", 10, "ITS-RS4-M"),
                model(1, "Commsignia", 11, "ITS-RS4-S"),
                model(2, "Kapsch", 20, "RIS-9260"),
                model(3, "OTA", 30, "OBU")));

        var options = new FirmwareUploadOptionsService(repository).getOptions();

        assertThat(options.manufacturers()).hasSize(2);
        assertThat(options.manufacturers().getFirst().name()).isEqualTo("Commsignia");
        assertThat(options.manufacturers().getFirst().fileExtension()).isEqualTo(".tar.sig");
        assertThat(options.manufacturers().getFirst().models())
                .extracting(option -> option.name())
                .containsExactly("ITS-RS4-M", "ITS-RS4-S");
        assertThat(options.manufacturers().get(1).name()).isEqualTo("Kapsch");
        assertThat(options.manufacturers())
                .noneMatch(option -> option.name().equalsIgnoreCase("ota"));
    }

    @Test
    void returnsAnEmptyManufacturerListWhenNoModelsExist() {
        var repository = mock(RsuModelRepository.class);
        when(repository.findAllWithManufacturerOrdered()).thenReturn(List.of());

        assertThat(new FirmwareUploadOptionsService(repository).getOptions().manufacturers()).isEmpty();
    }

    private RsuModel model(int manufacturerId, String manufacturerName, int modelId, String modelName) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(manufacturerId);
        manufacturer.setName(manufacturerName);
        manufacturer.setFirmwareFileExtension("Commsignia".equals(manufacturerName) ? ".tar.sig" : null);

        RsuModel model = new RsuModel();
        model.setId(modelId);
        model.setName(modelName);
        model.setManufacturer(manufacturer);
        return model;
    }
}
