package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Qualifier;
import us.dot.its.jpo.ode.api.models.messages.MessageType;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import org.apache.commons.lang3.tuple.Pair;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.ode.api.models.messages.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.ode.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static us.dot.its.jpo.ode.api.models.messages.MessageType.*;

@Component
@Slf4j
public class DecoderManager {

    public static final Map<String, Pair<MessageType, Integer>> startFlagsToTypesAndSizes = Map.of(
            "0014", Pair.of(MessageType.BSM, 500),
            "0012", Pair.of(MessageType.MAP, 2048),
            "0013", Pair.of(MessageType.SPAT, 1000),
            "001d", Pair.of(MessageType.SRM, 500),
            "001e", Pair.of(MessageType.SSM, 500),
            "001f", Pair.of(MessageType.TIM, 500));
    public static final Map<MessageType, String> typesToStartFlags = startFlagsToTypesAndSizes.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getValue().getLeft(), Map.Entry::getKey));
    public static final int HEADER_MINIMUM_SIZE = 20;

    public BsmDecoder bsmDecoder;
    public MapDecoder mapDecoder;
    public SpatDecoder spatDecoder;
    public SrmDecoder srmDecoder;
    public SsmDecoder ssmDecoder;
    public TimDecoder timDecoder;
    private Executor executor;

    public DecoderManager(BsmDecoder bsmDecoder, MapDecoder mapDecoder, SpatDecoder spatDecoder, SrmDecoder srmDecoder,
            SsmDecoder ssmDecoder, TimDecoder timDecoder,  @Qualifier("codecClientExecutor") Executor executorBean) {
        this.bsmDecoder = bsmDecoder;
        this.mapDecoder = mapDecoder;
        this.spatDecoder = spatDecoder;
        this.srmDecoder = srmDecoder;
        this.ssmDecoder = ssmDecoder;
        this.timDecoder = timDecoder;
        this.executor = executorBean;
    }

    /**
     * This function takes in an Encoded message object, and decodes it into a
     * DecodedMessage Object.
     * During the decoding process this function performs the following
     * Remove Message Headers
     * Pass the Message to the ACM module for Decoding
     * Pass the message to the appropriate Message type decoder to be converted to
     * the correct J2735 and Processed- message formats.
     * 
     * @return A DecodedMessage object representing the object in its multiple
     *         representations. This includes, asn.1, ODEJsonFormat, and Processed
     *         formats for available message types.
     */
    public CompletableFuture<? extends DecodedMessage> decode(EncodedMessage message) {
        log.info("EncodedMessage: {}", message);
        final String payload = removeHeader(message.getAsn1Message(), message.getType());
        message.setAsn1Message(payload);

        if (payload == null) {
            return CompletableFuture.supplyAsync(
                    () -> new DecodedMessage(null, message.getType(),
                    "Unable to find valid message start flag within input data"),
                    executor);
        }

        final Decoder decoder = switch (message.getType()) {
            case MessageType.BSM:
                yield bsmDecoder;
            case MessageType.MAP:
                yield mapDecoder;
            case MessageType.SPAT:
                yield spatDecoder;
            case MessageType.SRM:
                yield srmDecoder;
            case MessageType.SSM:
                yield ssmDecoder;
            case MessageType.TIM:
                yield timDecoder;
            case MessageType.UNKNOWN:
                yield null;
        };
        if (decoder == null) {
            return CompletableFuture.supplyAsync(
                    () -> new DecodedMessage(payload, message.getType(), "No Valid Decoder found for Message Type UNKNOWN"),
                    executor);
        } else {
            return decoder.decode(message);
        }
    }

    /**
     * This is a helper function to return the current time as an ISO formatted
     * String
     * 
     * @return An ISO formatted string representing the current time
     */
    public static String getCurrentIsoTimestamp() {
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        return utc.format(DateTimeFormatter.ISO_INSTANT);
    }

    public static long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    /**
     * This returns a static string representing the "Origin IP" for user-uploaded
     * data
     * 
     * @return "user-upload"
     */
    public static String getStaticUserOriginIp() {
        return "user-upload";
    }

    /**
     * This returns a Hex Encoded ASN.1 String where any header bytes before the
     * message frame type bytes have been removed.
     * 
     * @return A hexadecimal string representing an ASN.1 encoded message. The first
     *         4 characters of the hex string should correspond to an ASN.1 message
     *         type.
     */
    public static String removeHeader(String hexPacket, MessageType type) {

        String startFlag = typesToStartFlags.get(type);

        int startIndex = hexPacket.indexOf(startFlag);

        return switch (startIndex) {
            case 0:
                yield hexPacket; // Raw Message no Headers
            case -1:
                yield null;
            default:
                // We likely found a message with a header, look past the first 20
                // bytes for the start of the message
                int trueStartIndex = HEADER_MINIMUM_SIZE
                        + hexPacket.substring(HEADER_MINIMUM_SIZE).indexOf(startFlag);
                yield hexPacket.substring(trueStartIndex);
        };
    }

    /**
     * This method takes in a hex encoded ASN.1 packet and returns the message type
     * that matches the corresponding method.
     * 
     * @return An EncodedMessage object containing a String representing the hex
     *         encoded asn.1 and MessageType object representing MAP, SPaT, BSM,
     *         etc.
     */
    public static EncodedMessage identifyAsn1(String hexPacket) {

        int endIndex = hexPacket.length() - 1;

        int closestStartIndex = endIndex;
        MessageType closestMessageType = MessageType.UNKNOWN;

        for (Map.Entry<String, Pair<MessageType, Integer>> entry : startFlagsToTypesAndSizes.entrySet()) {

            String startFlag = entry.getKey();
            MessageType mType = entry.getValue().getLeft();
            int typeBufferSize = entry.getValue().getRight();

            // Skip possible message type if packet is too big
            if (endIndex > typeBufferSize * 2) {
                continue;
            }

            int startIndex = hexPacket.indexOf(startFlag);

            if (startIndex == 0) {
                return new EncodedMessage(hexPacket, mType);
            } else if (startIndex != -1) {
                int trueStartIndex = hexPacket.substring(HEADER_MINIMUM_SIZE).indexOf(startFlag);
                if (trueStartIndex == -1) {
                    continue;
                }
                trueStartIndex += HEADER_MINIMUM_SIZE;

                while ((trueStartIndex % 2 == 1) && trueStartIndex < hexPacket.length() - 4) {
                    // trueStartIndex != -1 is required by trueStartIndex % 2 == 1
                    int newStartIndex = hexPacket.substring(trueStartIndex + 1).indexOf(startFlag);
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
                }
            }
        }

        if (closestMessageType == MessageType.UNKNOWN) {
            return new EncodedMessage(hexPacket, MessageType.UNKNOWN);
        } else {
            return new EncodedMessage(hexPacket.substring(closestStartIndex), closestMessageType);
        }
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
        long numError = 0;
        while (scanner.hasNextLine()) {
            String typeTimestamp = scanner.nextLine();

            // Parse metadata line of format:
            // MessageType,timestamp

            String[] typeTimestampArr = typeTimestamp.split(",");
            if (typeTimestampArr.length != 2) {
                throw new IllegalArgumentException(
                        String.format("Type/timestamp line doesn't have 2 items: %s", typeTimestamp));
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

            // Read the XML line itself
            String xml = scanner.nextLine();
            ++numXml;
            try {
                OdeData odeData = switch (type) {
                    case SPAT -> {
                        ++numSpat;
                        yield spatDecoder.getOdeSpatDataFromMessageFrameXml(xml, timestamp);
                    }
                    case MAP -> {
                        ++numMap;
                        yield mapDecoder.getOdeMapDataFromMessageFrameXml(xml, timestamp);
                    }
                    case BSM -> {
                        ++numBsm;
                        yield bsmDecoder.getOdeBsmDataFromMessageFrameXml(xml, timestamp);
                    }
                    case SRM -> {
                        ++numSrm;
                        yield srmDecoder.getOdeSrmDataFromMessageFrameXml(xml, timestamp);
                    }
                    case SSM -> {
                        ++numSsm;
                        yield ssmDecoder.getOdeSsmDataFromMessageFrameXml(xml, timestamp);
                    }
                    case TIM -> {
                        ++numTim;
                        log.warn("TIM XML message, not supported: {}", xml);
                        yield null;
                    }
                    default -> {
                        ++numUnknown;
                        log.warn("Unknown XML message type: {}: {}", type, xml);
                        yield null;
                    }
                };
                odeDataList.add(odeData);
            } catch (Exception e) {
                ++numError;
                log.error("Error converting XML to OdeData: {}, xml: {}", e.getMessage(), xml);
            }

        }
        log.info("finished converting {} xml items to {} ode json items. " +
                "SPATs: {}, MAPs: {}, BSMs: {}, SRMs: {}, SSMs: {}, TIMs: {}, Unknown: {}, Error: {}",
                numXml, odeDataList.size(),
                numSpat, numMap, numBsm, numSrm, numSsm, numTim, numUnknown, numError);
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
                    log.error("Error converting to processed spat: {}, OdeSpatData: {}", e.getMessage(),
                            spatData.toJson());
                }
            } else if (data instanceof OdeMapData mapData) {
                try {
                    ProcessedMap<LineString> processedMap = mapDecoder.createProcessedMap(mapData);
                    decodedMessages.add(processedMap.toString());
                } catch (Exception e) {
                    log.error("Error converting to processed map: {}, OdeMapData: {}", e.getMessage(),
                            mapData.toJson());
                }
            } else {
                decodedMessages.add(data.toJson());
            }
        }
        return decodedMessages;
    }
}