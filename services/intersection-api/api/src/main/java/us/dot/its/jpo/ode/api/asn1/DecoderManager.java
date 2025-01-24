package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.ode.api.models.messages.MessageType;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.ode.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

@Component
@Slf4j
public class DecoderManager {

    public static final MessageType[] types = { MessageType.BSM, MessageType.MAP, MessageType.SPAT, MessageType.SRM,
            MessageType.SSM, MessageType.TIM };
    public static final String[] startFlags = { "0014", "0012", "0013", "001d", "001e", "001f" }; // BSM, MAP, SPAT,
                                                                                                  // SRM, SSM, TIM
    public static final int[] maxSizes = { 500, 2048, 1000, 500, 500, 500 };
    public static final int HEADER_MINIMUM_SIZE = 20;
    public static final int bufferSize = 2048;

    @Autowired
    public BsmDecoder bsmDecoder;

    @Autowired
    public MapDecoder mapDecoder;

    @Autowired
    public SpatDecoder spatDecoder;

    @Autowired
    public SrmDecoder srmDecoder;

    @Autowired
    public SsmDecoder ssmDecoder;

    @Autowired
    public TimDecoder timDecoder;

    public DecodedMessage decode(EncodedMessage message) {
        String payload = removeHeader(message.getAsn1Message(), message.getType());
        message.setAsn1Message(payload);

        Decoder decoder = null;

        if (payload != null) {
            if (message.getType() == MessageType.BSM) {
                decoder = new BsmDecoder();
            } else if (message.getType() == MessageType.MAP) {
                decoder = mapDecoder;
            } else if (message.getType() == MessageType.SPAT) {
                decoder = spatDecoder;
            } else if (message.getType() == MessageType.SRM) {
                decoder = srmDecoder;
            } else if (message.getType() == MessageType.SSM) {
                decoder = ssmDecoder;
            } else if (message.getType() == MessageType.TIM) {
                decoder = timDecoder;
            } else {
                return new DecodedMessage(payload, message.getType(), "No Valid Decoder found for Message Type");
            }

            return decoder.decode(message);

        }

        return new DecodedMessage(payload, message.getType(),
                "Unable to find valid message start flag within input data");

    }

    public static String getOdeReceivedAt() {
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        String timestamp = utc.format(DateTimeFormatter.ISO_INSTANT);
        return timestamp;
    }

    public static String getOriginIp() {
        return "user-upload";
    }

    public static String removeHeader(String hexPacket, MessageType type) {

        String startFlag = startFlags[ArrayUtils.indexOf(types, type)];

        int startIndex = hexPacket.indexOf(startFlag);
        if (startIndex == 0) {
            // Raw Message no Headers
        } else if (startIndex == -1) {

            return null;
        } else {
            // We likely found a message with a header, look past the first 20
            // bytes for the start of the BSM
            int trueStartIndex = HEADER_MINIMUM_SIZE
                    + hexPacket.substring(HEADER_MINIMUM_SIZE, hexPacket.length()).indexOf(startFlag);
            hexPacket = hexPacket.substring(trueStartIndex, hexPacket.length());
        }

        return hexPacket;
    }

    public static EncodedMessage identifyAsn1(String hexPacket) {
        int endIndex = hexPacket.length() - 1;

        int closestStartIndex = endIndex;
        MessageType closestMessageType = MessageType.UNKNOWN;

        for (int i = 0; i < startFlags.length; i++) {

            String startFlag = startFlags[i];
            MessageType mType = types[i];
            int typeBufferSize = maxSizes[i];

            // Skip possible message type if packet is too big
            if (endIndex > typeBufferSize * 2) {
                continue;
            }

            int startIndex = hexPacket.indexOf(startFlag);

            if (startIndex == 0) {
                return new EncodedMessage(hexPacket, mType);
            } else if (startIndex == -1) {
                continue;
            } else {
                int trueStartIndex = hexPacket.substring(HEADER_MINIMUM_SIZE, hexPacket.length()).indexOf(startFlag);
                if (trueStartIndex == -1) {
                    continue;
                }
                trueStartIndex += HEADER_MINIMUM_SIZE;

                while (trueStartIndex != -1 && (trueStartIndex % 2 == 1) && trueStartIndex < hexPacket.length() - 4) {
                    int newStartIndex = hexPacket.substring(trueStartIndex + 1, hexPacket.length()).indexOf(startFlag);
                    if (newStartIndex == -1) {
                        trueStartIndex = -1;
                        break;
                    } else {
                        trueStartIndex += newStartIndex + 1;
                    }
                }

                if (trueStartIndex != -1 && trueStartIndex < closestStartIndex) {
                    closestStartIndex = trueStartIndex;
                    closestMessageType = mType;
                    // closestBufferSize = typeBufferSize;
                }
            }
        }

        if (closestMessageType == MessageType.UNKNOWN) {
            return new EncodedMessage(hexPacket, MessageType.UNKNOWN);
        } else {
            return new EncodedMessage(hexPacket.substring(closestStartIndex, hexPacket.length()), closestMessageType);
        }
    }

    public static String decodeXmlWithAcm(String xmlMessage) throws Exception {

        log.info("Decoding Message: " + xmlMessage);
        log.info("Decoding message: {}", xmlMessage);

        // Save XML to temp file
        String tempDir = FileUtils.getTempDirectoryPath();
        String tempFileName = "asn1-codec-java-" + UUID.randomUUID().toString() + ".xml";
        log.info("Temp file name: {}", tempFileName);
        log.info("Temp File Name: " + tempFileName);
        Path tempFilePath = Path.of(tempDir, tempFileName);
        File tempFile = new File(tempFilePath.toString());
        FileUtils.writeStringToFile(tempFile, xmlMessage, StandardCharsets.UTF_8);

        // Run ACM tool to decode message
        var pb = new ProcessBuilder(
                "/build/acm", "-F", "-c", "/build/config/example.properties", "-T", "decode",
                tempFile.getAbsolutePath());
        pb.directory(new File("/build"));
        Process process = pb.start();
        String result = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        log.info("Result: {}", result);
        log.info("Decode Result: " + result);

        // Clean up temp file
        tempFile.delete();

        return result;
    }

    public List<OdeData> convertBatchXmlToOdeData(String xmlBatch) {
        Scanner scanner = new Scanner(xmlBatch);
        List<OdeData> odeDataList = new ArrayList<>();
        log.info("Converting xml to ode json");
        long numXml = 0;
        long numSpat = 0;
        long numMap = 0;
        long numBsm = 0;
        long numSrm = 0;
        long numSsm = 0;
        long numTim = 0;
        long numUnknown = 0;
        while (scanner.hasNextLine()) {
            String typeTimestamp = scanner.nextLine();

            // Parse line of format:
            // MessageType,timestamp

            String[] typeTimestampArr = typeTimestamp.split(",");
            if (typeTimestampArr.length != 2) {
                throw new IllegalArgumentException(String.format("Type/timestamp line doesn't have 2 items: %s", typeTimestamp));
            }

            MessageType type;
            try {
                type = MessageType.valueOf(typeTimestampArr[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Invalid message type in %s", typeTimestamp), e);
            }

            long timestamp;
            try {
                timestamp = Long.parseLong(typeTimestampArr[1]);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Invalid timestamp format in %s", typeTimestamp), e);
            }

            // Read XML line
            String xml = scanner.nextLine();
            ++numXml;
            try {
                OdeData odeData = switch (type) {
                    case SPAT -> { ++numSpat; yield spatDecoder.getOdeSpatDataFromMessageFrameXml(xml, timestamp); }
                    case MAP -> { ++numMap; yield mapDecoder.getOdeMapDataFromMessageFrameXml(xml, timestamp); }
                    case BSM -> { ++numBsm; yield bsmDecoder.getOdeBsmDataFromMessageFrameXml(xml, timestamp); }
                    case SRM -> { ++numSrm; yield srmDecoder.getOdeSrmDataFromMessageFrameXml(xml, timestamp); }
                    case SSM -> { ++numSsm; yield ssmDecoder.getOdeSsmDataFromMessageFrameXml(xml, timestamp); }
                    case TIM -> { ++numTim; log.warn("TIM XML message, not supported: {}", xml); yield null; }
                    default -> { ++numUnknown; log.warn("Unknown XML message type: {}: {}", type, xml); yield null; }
                };
                odeDataList.add(odeData);
            } catch (Exception e) {
                log.error("Error converting XML to OdeData: {}, xml: {}", e.getMessage(), xml);
            }

        }
        log.info("finished converting {} xml items to {} ode json items. " +
                "SPATs: {}, MAPs: {}, BSMs: {}, SRMs: {}, SSMs: {}, TIMs: {}, Unknown: {}{",
                numXml, odeDataList.size(),
                numSpat, numMap, numBsm, numSrm, numSsm, numTim, numUnknown);
        return odeDataList;
    }

    public List<String> convertBatchOdeDataToJson(List<OdeData> odeDataList) {
        List<String> decodedMessages = new ArrayList<>();
        for (OdeData data : odeDataList) {
            if (data instanceof OdeSpatData spatData) {
                try {
                    ProcessedSpat processedSpat = spatDecoder.createProcessedSpat(spatData);
                    decodedMessages.add(processedSpat.toString());
                } catch (Exception e) {
                    log.error("Error converting to processed spat: {}, OdeSpatData: {}", e.getMessage(), spatData.toJson());
                }
            } else if (data instanceof OdeMapData mapData) {
                try {
                    ProcessedMap<LineString> processedMap = mapDecoder.createProcessedMap(mapData);
                    decodedMessages.add(processedMap.toString());
                } catch (Exception e) {
                    log.error("Error converting to processed map: {}, OdeMapData: {}", e.getMessage(), mapData.toJson());
                }
            } else {
                decodedMessages.add(data.toJson());
            }
        }
        return decodedMessages;
    }
}