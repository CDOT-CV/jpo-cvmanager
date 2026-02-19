package us.dot.its.jpo.ode.mockdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

import us.dot.its.jpo.asn.j2735.r2024.MapData.MapData;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;

@Slf4j
public class MockMapGenerator {

    static TypeReference<ProcessedMap<LineString>> typeReference = new TypeReference<>() {
    };

    public static List<ProcessedMap<LineString>> getProcessedMaps() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<ProcessedMap<LineString>> maps = new ArrayList<>();

        try {
            String processedMapString = new String(
                    Files.readAllBytes(Path.of("src/main/resources/mockdata/processed_map.json")));
            ProcessedMap<LineString> map = objectMapper.readValue(processedMapString, typeReference);
            maps.add(map);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return maps;
    }

    public static List<MapData> getJsonMaps() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<MapData> maps = new ArrayList<>();

        try {
            String mapString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/map.json")));
            MapData map = objectMapper.readValue(mapString, MapData.class);
            maps.add(map);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return maps;
    }
}
