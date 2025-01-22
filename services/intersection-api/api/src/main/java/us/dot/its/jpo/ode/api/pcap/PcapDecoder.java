package us.dot.its.jpo.ode.api.pcap;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import static org.apache.commons.lang3.StringUtils.*;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
import java.nio.file.Path;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import java.util.Optional;


@Component
@Slf4j
public class PcapDecoder {

    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Use the tshark command line tool to decode pcap bytes to json
     * @param bytes
     * @return json
     * @throws IOException
     */
    public String pcapToJson(byte[] bytes) throws IOException {
        return decodePcap(bytes, new String[] { 
            "-T", "json", "-x"
        });
    }

    public TimestampedHexList parsePcapJson(String json) throws JsonMappingException, JsonProcessingException {
        JsonNode[] nodeArr = mapper.readerForArrayOf(JsonNode.class).readValue(json);
        var hexList = new TimestampedHexList();
        for (JsonNode frame : nodeArr) {
            parsePcapFrame(frame.toString()).ifPresent(hex -> hexList.add(hex));
        }
        return hexList;
    }

    public Optional<TimestampedHex> parsePcapFrame(String frameJson) {

        final DocumentContext context = JsonPath.parse(frameJson);
        final DocumentContext pathContext = JsonPath.using(Configuration.builder().options(Option.AS_PATH_LIST).build()).parse(frameJson);
        final var hexData = new TimestampedHex();
   
        JSONArray timestampArr = context.read("$..['frame.time_epoch']");
        if (timestampArr.isEmpty()) return Optional.empty();
        String timestampStr = (String)timestampArr.getFirst();
        long epochMillis;
        try {
            epochMillis = (long)(Double.parseDouble(timestampStr) * 1000);
        } catch (Exception e) {
            log.error("Error parsing timestamp in frame {}", frameJson, e);
            return Optional.empty();
        }
        hexData.setTimestamp(epochMillis);
        
        // Look for bytes in order of most to least desirable form.
        // Prefer unsecured Data, no further processing needed
        if (extractFromJson(context, pathContext, hexData, "$..['ieee1609dot2.unsecuredData_raw'][0]")) return Optional.of(hexData);

        // UDP payload
        if (extractFromJson(context, pathContext, hexData, "$..['udp.payload_raw'][0]")) return Optional.of(hexData);

        // Raw data frame
        if (extractFromJson(context, pathContext, hexData, "$..frame_raw[0]")) return Optional.of(hexData);

        // No hex data found
        return Optional.empty();
    }

    private boolean extractFromJson(DocumentContext context, DocumentContext pathContext, TimestampedHex hexData, String path) {
        JSONArray dataArr = context.read(path);
        String data = !dataArr.isEmpty() ? (String)dataArr.getFirst() : null;
        if (isNotBlank(data)) {
            List<String> pathList = pathContext.read(path);
            // TOTO: Process
            hexData.setHexMessage(data);
            hexData.setPath(pathList.getFirst());
            return true;
        }
        return false;
    }


//    /**
//     * Extract timestamps and hex values from tshark CSV output
//     * @param csv format: timestamp, udp.payload, IEEE 1690.2 unsecured data
//     * @return Timestmaped hex
//     */
//    public TimestampedHexList parseCsvFile(String csv) {
//        var hexList = new TimestampedHexList();
//        try (var scanner = new Scanner(csv)) {
//            while (scanner.hasNextLine()) {
//                String line = scanner.nextLine();
//                parseCsvLine(line).ifPresent(hex -> hexList.add(hex));
//            }
//        }
//        return hexList;
//    }

//    public Optional<TimestampedHex> parseCsvLine(String line) {
//        if (isBlank(line)) {
//            log.warn("CSV line is empty");
//            return Optional.empty();
//        };
//
//        // Get hex from the second or third csv item for UDP or WAVE
//        String[] lineArr = line.split(",");
//        if (lineArr.length != 3 && lineArr.length != 2) {
//            log.warn("CSV line should have 2 or 3 items: {}", line);
//            return Optional.empty();
//        }
//        String hex = lineArr.length == 3 ? lineArr[2] : lineArr[1];
//
//        var tsHex = new TimestampedHex();
//
//        // Parse and validate hex
//        try {
//            tsHex.setHexMessage(hex);
//        } catch (Exception e) {
//            log.error("Hex is invalid in csv line {}", line, e);
//            return Optional.empty();
//        }
//
//        // Get timestamp
//        long epochMillis;
//        try {
//            epochMillis = (long)(Double.parseDouble(lineArr[0]) * 1000);
//        } catch (Exception e) {
//            log.error("Error parsing timestamp in csv line {}", line, e);
//            return Optional.empty();
//        }
//        tsHex.setTimestamp(epochMillis);
//
//
//
//        return Optional.of(tsHex);
//    }

    private String decodePcap(byte[] bytes, String[] tsharkOptions) throws IOException {
        String result = null;
        File tempFile = null;
        try {
            String tempDir = FileUtils.getTempDirectoryPath();
            String tempFileName = "tshark-" + UUID.randomUUID().toString() + ".pcap";
            Path tempFilePath = Path.of(tempDir, tempFileName);
            tempFile = new File(tempFilePath.toString());
            FileUtils.writeByteArrayToFile(tempFile, bytes);
            String[] tsharkCommand = new String[] { "/usr/bin/tshark", "-r", tempFile.getAbsolutePath()};
            String[] tsharkCommandWithOptions = ArrayUtils.addAll(tsharkCommand, tsharkOptions);
            var pb = new ProcessBuilder(tsharkCommandWithOptions);
            pb.directory(new File(tempDir));
            Process process = pb.start();
            result = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
        return result;
    }
}
