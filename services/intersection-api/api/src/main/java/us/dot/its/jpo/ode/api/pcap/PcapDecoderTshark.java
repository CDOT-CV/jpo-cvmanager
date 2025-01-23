//package us.dot.its.jpo.ode.api.pcap;
//
//import java.util.List;
//import java.util.UUID;
//
//import com.jayway.jsonpath.*;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.io.IOUtils;
//import org.apache.commons.lang3.ArrayUtils;
//import static org.apache.commons.lang3.StringUtils.*;
//import org.springframework.stereotype.Component;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonMappingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import lombok.extern.slf4j.Slf4j;
//import net.minidev.json.JSONArray;
//import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
//import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
//import java.nio.file.Path;
//import java.io.File;
//import java.nio.charset.StandardCharsets;
//import java.io.IOException;
//
//import java.util.Optional;
//
//
//@Component("tsharkPcapDecoder")
//@Slf4j
//public class PcapDecoderTshark implements PcapDecoder {
//
//    @Override
//    public TimestampedHexList decodePcap(byte[] bytes) throws IOException {
//        return parsePcapJson(decodeVerbosely(bytes));
//    }
//
//    /**
//     * Use the tshark command line tool to decode pcap bytes to json
//     * @param bytes
//     * @return json
//     * @throws IOException
//     */
//    @Override
//    public String decodeVerbosely(byte[] bytes) throws IOException {
//        log.info("pcapToJson received {} bytes", bytes.length);
//        return decodePcap(bytes, new String[] {
//                "-T", "json", "-x"
//        });
//    }
//
//    private static final ObjectMapper mapper = new ObjectMapper();
//
//    private String decodePcap(byte[] bytes, String[] tsharkOptions) throws IOException {
//        log.info("decodePcap");
//        String result = null;
//        File tempFile = null;
//        try {
//            String tempDir = FileUtils.getTempDirectoryPath();
//            String tempFileName = "tshark-" + UUID.randomUUID().toString() + ".pcap";
//            Path tempFilePath = Path.of(tempDir, tempFileName);
//            tempFile = new File(tempFilePath.toString());
//            FileUtils.writeByteArrayToFile(tempFile, bytes);
//            String[] tsharkCommand = new String[] { "/usr/bin/tshark", "-r", tempFile.getAbsolutePath()};
//            String[] tsharkCommandWithOptions = ArrayUtils.addAll(tsharkCommand, tsharkOptions);
//            var pb = new ProcessBuilder(tsharkCommandWithOptions);
//            pb.directory(new File(tempDir));
//            Process process = pb.start();
//            result = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
//        } finally {
//            if (tempFile != null) {
//                tempFile.delete();
//            }
//        }
//        log.info("finished decodePcap");
//        return result;
//    }
//
//    public TimestampedHexList parsePcapJson(String json) throws JsonMappingException, JsonProcessingException {
//        log.info("parsePcapJson");
//        JsonNode[] nodeArr = mapper.readerForArrayOf(JsonNode.class).readValue(json);
//        var hexList = new TimestampedHexList();
//        for (JsonNode frame : nodeArr) {
//            parsePcapFrame(frame.toString()).ifPresent(hex -> hexList.add(hex));
//        }
//        log.info("finished parsePcapJson");
//        return hexList;
//    }
//
//    public Optional<TimestampedHex> parsePcapFrame(String frameJson) {
//
//        final DocumentContext context = JsonPath.parse(frameJson);
//        final DocumentContext pathContext = JsonPath.using(Configuration.builder().options(Option.AS_PATH_LIST).build()).parse(frameJson);
//        final var hexData = new TimestampedHex();
//
//        JSONArray timestampArr = context.read("$..['frame.time_epoch']");
//        if (timestampArr.isEmpty()) return Optional.empty();
//        String timestampStr = (String)timestampArr.getFirst();
//        long epochMillis;
//        try {
//            epochMillis = (long)(Double.parseDouble(timestampStr) * 1000);
//        } catch (Exception e) {
//            log.error("Error parsing timestamp in frame {}", frameJson, e);
//            return Optional.empty();
//        }
//        hexData.setTimestamp(epochMillis);
//
//        // Look for bytes in order of most to least desirable form.
//        // Prefer unsecured Data, no further processing needed
//        if (extractFromJson(context, pathContext, hexData, "$..['ieee1609dot2.unsecuredData_raw'][0]")) return Optional.of(hexData);
//
//        // UDP payload
//        if (extractFromJson(context, pathContext, hexData, "$..['udp.payload_raw'][0]")) return Optional.of(hexData);
//
//        // Raw data frame
//        if (extractFromJson(context, pathContext, hexData, "$..frame_raw[0]")) return Optional.of(hexData);
//
//        // No hex data found
//        return Optional.empty();
//    }
//
//    private boolean extractFromJson(DocumentContext context, DocumentContext pathContext, TimestampedHex hexData, String path) {
//        JSONArray dataArr = context.read(path);
//        String data = !dataArr.isEmpty() ? (String)dataArr.getFirst() : null;
//        if (isNotBlank(data)) {
//            List<String> pathList = pathContext.read(path);
//            hexData.setPath(pathList.getFirst());
//            hexData.setRawDataHex(data);
//            return true;
//        }
//        return false;
//    }
//
//
//
//}
