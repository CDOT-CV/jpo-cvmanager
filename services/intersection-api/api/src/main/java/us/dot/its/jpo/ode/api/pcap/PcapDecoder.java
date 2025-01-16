package us.dot.its.jpo.ode.api.pcap;

import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
import java.nio.file.Path;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Scanner;


@Component
@Slf4j
public class PcapDecoder {

    /**
     * Extract timestamps and hex values from tshark CSV output
     * @param csv format: timestamp, udp.payload, IEEE 1690.2 unsecured data
     * @return Timestmaped hex
     */
    public TimestampedHexList csvToTimestampedHexList(String csv) {
        var hexList = new TimestampedHexList();
        // TODO
        return hexList;
    }

    /**
     * Use the tshark command line tool to decode pcap bytes to json
     * @param bytes
     * @return json
     * @throws IOException
     */
    public String pcapToJson(byte[] bytes) throws IOException {
        return decodePcap(bytes, new String[] { 
            "-T", "json" 
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

    public TimestampedHexList parseCsvFile(String csv) {
        var hexList = new TimestampedHexList();
        try (var scanner = new Scanner(csv)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                parseCsvLine(line).ifPresent(hex -> hexList.add(hex);
            }
        }
        return hexList;
    }

    public Optional<TimestampedHex> parseCsvLine(String line) {
        if (StringUtils.isBlank(line)) {
            log.warn("CSV line is empty");
            return Optional.empty();
        };
        String[] lineArr = line.split(",");
        if (lineArr.length != 3) {
            log.error("CSV line should have 3 items");
            return Optional.empty();
        }

        long epochMillis;
        try {
            epochMillis = (long)(Double.parseDouble(lineArr[0]) * 1000);
        } catch (Exception e) {
            log.error("Error parsing timestamp in csv line {}", line, e);
            return Optional.empty();
        }

        
        String udpHex = lineArr[1];
        String waveHex = lineArr[2];
        String hex = StringUtils.isNotBlank(udpHex) ? udpHex : waveHex;

        HexFormat hexFormat = HexFormat.of();
        byte[] bytes;
        try {
            bytes = hexFormat.parseHex(hex);
        } catch (Exception e) {
            log.error("Hex is invalid in csv line {}", line, e);
            return Optional.empty();
        }

        var tsHex = new TimestampedHex();
        tsHex.setTimestamp(epochMillis);
        tsHex.setHexMessage(hex);
        tsHex.setBytes(bytes);
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
