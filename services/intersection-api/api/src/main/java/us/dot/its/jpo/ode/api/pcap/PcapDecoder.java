package us.dot.its.jpo.ode.api.pcap;

import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import static org.apache.commons.lang3.StringUtils.*;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.DocumentContext;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
import java.nio.file.Path;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import java.util.Optional;
import java.util.Scanner;


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

    /**
     * Use the tshark command line tool to decode pcap to CSV with format:
     *   timestamp, udp.payload, IEEE 1601.2 unsecured data
     * For UDP or unsecured WAVE data.
     * @param bytes
     * @return CSV
     * @throws IOException
     */
    public String pcapToCsv(byte[] bytes) throws IOException {
        return decodePcap(bytes, new String[] {
                "-T", "fields",
                "-E", "separator=,",
                "-e", "frame.time_epoch",
                "-e", "udp.payload",
                "-e", "ieee1609dot2.unsecuredData"
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

    private Optional<TimestampedHex> parsePcapFrame(String frameJson) {
        DocumentContext context = JsonPath.parse(frameJson);
        var hexData = new TimestampedHex();
        
        String timestamp = context.read("$..['frame.time_epoch']");
        long epochMillis;
        try {
            epochMillis = (long)(Double.parseDouble(timestamp) * 1000);
        } catch (Exception e) {
            log.error("Error parsing timestamp in frame {}", frameJson, e);
            return Optional.empty();
        }
        hexData.setTimestamp(epochMillis);
        
        // Look for bytes in order of most to least desirable form.
        // Prefer unsecured Data, no further processing needed
        String unsecuredData = context.read("$..['ieee1609dot2.unsecuredData_raw']");
        if (isNotBlank(unsecuredData)) {
            hexData.setUnsecuredData(unsecuredData);
            hexData.setHexMessage(unsecuredData);
            return Optional.of(hexData);
        }

        // UDP payload
        String udpPayload = context.read("$..['udp.payload_raw']");
        if (isNotBlank(udpPayload)) {
            hexData.setUdpPayload(udpPayload);
            // TODO: Process
            hexData.setHexMessage(udpPayload);
            return Optional.of(hexData);
        }

        // Raw data frame
        String rawFrame = context.read("$..frame_raw");
        if (isNotBlank(rawFrame)) {
            hexData.setRawFrame(rawFrame);
            // TODO: Process
            hexData.setHexMessage(rawFrame);
            return Optional.of(hexData);
        }
        
        // No hex data found
        return Optional.empty();
    }

    /**
     * Extract timestamps and hex values from tshark CSV output
     * @param csv format: timestamp, udp.payload, IEEE 1690.2 unsecured data
     * @return Timestmaped hex
     */
    public TimestampedHexList parseCsvFile(String csv) {
        var hexList = new TimestampedHexList();
        try (var scanner = new Scanner(csv)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                parseCsvLine(line).ifPresent(hex -> hexList.add(hex));
            }
        }
        return hexList;
    }

    public Optional<TimestampedHex> parseCsvLine(String line) {
        if (isBlank(line)) {
            log.warn("CSV line is empty");
            return Optional.empty();
        };

        // Get hex from the second or third csv item for UDP or WAVE
        String[] lineArr = line.split(",");
        if (lineArr.length != 3 && lineArr.length != 2) {
            log.warn("CSV line should have 2 or 3 items: {}", line);
            return Optional.empty();
        }
        String hex = lineArr.length == 3 ? lineArr[2] : lineArr[1];

        var tsHex = new TimestampedHex();
    
        // Parse and validate hex
        try {
            tsHex.setHexMessage(hex);
        } catch (Exception e) {
            log.error("Hex is invalid in csv line {}", line, e);
            return Optional.empty();
        }

        // Get timestamp
        long epochMillis;
        try {
            epochMillis = (long)(Double.parseDouble(lineArr[0]) * 1000);
        } catch (Exception e) {
            log.error("Error parsing timestamp in csv line {}", line, e);
            return Optional.empty();
        }
        tsHex.setTimestamp(epochMillis);

        

        return Optional.of(tsHex);
    }

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
