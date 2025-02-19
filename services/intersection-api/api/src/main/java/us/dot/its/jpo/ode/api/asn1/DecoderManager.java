package us.dot.its.jpo.ode.api.asn1;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.api.models.messages.MessageType;
import us.dot.its.jpo.ode.api.models.messages.TimestampedOdeData;
import us.dot.its.jpo.ode.api.models.messages.TimestampedOdeDataList;
import us.dot.its.jpo.ode.model.OdeData;
import us.dot.its.jpo.ode.model.OdeMapData;
import us.dot.its.jpo.ode.model.OdeSpatData;

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
  public static final Map<MessageType, String> typesToStartFlags = startFlagsToTypesAndSizes.entrySet()
      .stream()
      .collect(Collectors.toMap(entry -> entry.getValue().getLeft(), Map.Entry::getKey));
  public static final int HEADER_MINIMUM_SIZE = 20;

  public BsmDecoder bsmDecoder;
  public MapDecoder mapDecoder;
  public SpatDecoder spatDecoder;
  public SrmDecoder srmDecoder;
  public SsmDecoder ssmDecoder;
  public TimDecoder timDecoder;

  public DecoderManager(BsmDecoder bsmDecoder, MapDecoder mapDecoder, SpatDecoder spatDecoder,
      SrmDecoder srmDecoder,
      SsmDecoder ssmDecoder, TimDecoder timDecoder) {
    this.bsmDecoder = bsmDecoder;
    this.mapDecoder = mapDecoder;
    this.spatDecoder = spatDecoder;
    this.srmDecoder = srmDecoder;
    this.ssmDecoder = ssmDecoder;
    this.timDecoder = timDecoder;
  }

  /**
   * This function takes in an Encoded message object, and decodes it into a DecodedMessage Object.
   * During the decoding process this function performs the following Remove Message Headers Pass
   * the Message to the ACM module for Decoding Pass the message to the appropriate Message type
   * decoder to be converted to the correct J2735 and Processed- message formats.
   *
   * @return A DecodedMessage object representing the object in its multiple representations. This
   * includes, asn.1, ODEJsonFormat, and Processed formats for available message types.
   */
  public CompletableFuture<? extends DecodedMessage> decode(EncodedMessage message) {
    log.info("EncodedMessage: {}", message);
    final String payload = removeHeader(message.getAsn1Message(), message.getType());
    message.setAsn1Message(payload);

    if (payload == null) {
      return CompletableFuture.completedFuture(
          new DecodedMessage(null, message.getType(),
              "Unable to find valid message start flag within input data"));
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
      return CompletableFuture.completedFuture(
          new DecodedMessage(payload, message.getType(),
              "No Valid Decoder found for Message Type UNKNOWN"));
    } else {
      return decoder.decode(message);
    }
  }

  /**
   * This is a helper function to return the current time as an ISO formatted String
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
   * This returns a static string representing the "Origin IP" for user-uploaded data
   *
   * @return "user-upload"
   */
  public static String getStaticUserOriginIp() {
    return "user-upload";
  }

  /**
   * This returns a Hex Encoded ASN.1 String where any header bytes before the message frame type
   * bytes have been removed.
   *
   * @return A hexadecimal string representing an ASN.1 encoded message. The first 4 characters of
   * the hex string should correspond to an ASN.1 message type.
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
   * This method takes in a hex encoded ASN.1 packet and returns the message type that matches the
   * corresponding method.
   *
   * @return An EncodedMessage object containing a String representing the hex encoded asn.1 and
   * MessageType object representing MAP, SPaT, BSM, etc.
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

  public String convertBatchXmlToOdeJson(String xmlBatch, int expectedNumberOfMessages) {
    log.info("Received xmlBatch of {} chars", xmlBatch != null ? xmlBatch.length() : 0);

    if (xmlBatch == null) {
      log.error("The batch result is empty");

      // Use a runtime exception because if this were a checked exception it would
      // need to be wrapped in a runtime exception in the lambda that calls this anyway.
      throw new RuntimeException("Codec returned empty batch XML or timed out");
    }

    TimestampedOdeDataList odeDataList = convertBatchXmlToOdeData(xmlBatch);

    return convertBatchOdeDataToJson(odeDataList, expectedNumberOfMessages);
  }

  private TimestampedOdeDataList convertBatchXmlToOdeData(String xmlBatch) {
    Scanner scanner = new Scanner(xmlBatch);
    var odeDataList = new TimestampedOdeDataList();
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
        throw new IllegalArgumentException(
            String.format("Invalid message type in %s", typeTimestamp), e);
      }

      long timestamp;
      try {
        timestamp = Long.parseLong(typeTimestampArr[1]);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            String.format("Invalid timestamp format in %s", typeTimestamp), e);
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
        if (odeData != null) {
          var timestampedOdeData = new TimestampedOdeData();
          timestampedOdeData.setTimestamp(timestamp);
          timestampedOdeData.setType(type);
          timestampedOdeData.setOdeData(odeData);
          odeDataList.add(timestampedOdeData);
        }

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


  private String convertBatchOdeDataToJson(TimestampedOdeDataList odeDataList,
      final int expectedNumberOfMessages) {
    if (odeDataList == null) {
      log.error("odeDataList is null");
      return "";
    }
    List<String> decodedMessages = new ArrayList<>();
    for (TimestampedOdeData timestampedData : odeDataList) {
      OdeData data = timestampedData.getOdeData();
      String odeDataJson;
      if (data instanceof OdeSpatData spatData) {
        try {
          ProcessedSpat processedSpat = spatDecoder.createProcessedSpat(spatData);
          odeDataJson = processedSpat.toString();
        } catch (Exception e) {
          log.error("Error converting to processed spat: {}, OdeSpatData: {}", e.getMessage(),
              spatData.toJson());
          continue;
        }
      } else if (data instanceof OdeMapData mapData) {
        try {
          ProcessedMap<LineString> processedMap = mapDecoder.createProcessedMap(mapData);
          odeDataJson = processedMap.toString();
        } catch (Exception e) {
          log.error("Error converting to processed map: {}, OdeMapData: {}", e.getMessage(),
              mapData.toJson());
          continue;
        }
      } else  {
        odeDataJson = data.toJson();
      }
      String wrappedJson = String.format("""
          {"timestamp":%s,"type":%s,%s}
          """, timestampedData.getTimestamp(), timestampedData.getType(), odeDataJson);
      decodedMessages.add(wrappedJson);
    }
    log.info("Finished converting ode Json to {} processed json items", decodedMessages.size());
    if (decodedMessages.size() < expectedNumberOfMessages) {
      log.warn("{} items were dropped due to errors or unknown message types while " +
              "converting message frames to json",
          expectedNumberOfMessages - decodedMessages.size());
    }
    return "[" + String.join(",", decodedMessages) + "]";
  }
}