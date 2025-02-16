package us.dot.its.jpo.ode.api.asn1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.dot.its.jpo.ode.api.models.messages.MapDecodedMessage;
import us.dot.its.jpo.geojsonconverter.converter.map.MapProcessedJsonConverter;
import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.connectinglanes.ConnectingLanesFeatureCollection;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.MapFeatureCollection;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.MapSharedProperties;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.validator.JsonValidatorResult;
import us.dot.its.jpo.geojsonconverter.validator.MapJsonValidator;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.context.AppContext;
import us.dot.its.jpo.ode.model.*;
import us.dot.its.jpo.ode.model.Asn1Encoding.EncodingRule;
import us.dot.its.jpo.ode.model.OdeMapMetadata.MapSource;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.model.OdeLogMetadata.SecurityResultCode;
import us.dot.its.jpo.ode.plugin.j2735.J2735IntersectionGeometry;
import us.dot.its.jpo.ode.plugin.j2735.builders.MAPBuilder;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.util.XmlUtils.XmlUtilsException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class MapDecoder implements Decoder {
    private static final Logger logger = LoggerFactory.getLogger(MapDecoder.class);

    private final MapJsonValidator mapJsonValidator;

    private final CodecClient codecClient;

    @Autowired
    public MapDecoder(MapJsonValidator mapJsonValidator, CodecClient codecClient) {
        this.mapJsonValidator = mapJsonValidator;
        this.codecClient = codecClient;
    }

    public MapProcessedJsonConverter converter = new MapProcessedJsonConverter();

    @Override
    public CompletableFuture<MapDecodedMessage> decode(EncodedMessage message) {

        // Send String through ASN.1 Decoder to get Decoded XML Data
        CompletableFuture<String> messageFrameXmlFuture = codecClient.decodeSingle(message.getAsn1Message());

        return messageFrameXmlFuture.thenApply(messageFrameXml -> {
            // Convert to Ode Json
            OdeMapData map = null;
            try {
                map = getOdeMapDataFromMessageFrameXml(messageFrameXml, DecoderManager.getCurrentTimestamp());
                ProcessedMap<LineString> processedMap = createProcessedMap(map);
                // build output data structure
                return new MapDecodedMessage(processedMap, map, message.getAsn1Message(), "");
            } catch (XmlUtilsException e) {
                logger.error("XML Exception: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            logger.error("Generic Exception: {}", ex.getMessage(), ex);
            return new MapDecodedMessage(null, null, message.getAsn1Message(), ex.getMessage());
        });
    }

    @Override
    public OdeData getAsOdeData(String encodedData) {
        OdeMsgPayload payload = new OdeAsn1Payload(new OdeHexByteArray(encodedData));

        // construct metadata
        OdeMapMetadata metadata = new OdeMapMetadata(payload);

        metadata.setOdeReceivedAt(DecoderManager.getCurrentIsoTimestamp());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(RecordType.mapTx);
        metadata.setSecurityResultCode(SecurityResultCode.success);

        metadata.setMapSource(MapSource.RSU);

        Asn1Encoding unsecuredDataEncoding = new Asn1Encoding("unsecuredData", "MessageFrame",
                EncodingRule.UPER);
        metadata.addEncoding(unsecuredDataEncoding);

        // construct odeData
        return new OdeAsn1Data(metadata, payload);
    }

    @Override
    public OdeMapData getAsOdeJson(String consumedData) throws XmlUtilsException {
        ObjectNode consumed = XmlUtils.toObjectNode(consumedData);

        JsonNode metadataNode = consumed.findValue(AppContext.METADATA_STRING);
        if (metadataNode instanceof ObjectNode object) {
            // Removing encodings to match ODE behavior
            object.remove(AppContext.ENCODINGS_STRING);

            // Map header file does not have a location and use predefined set required
            // RxSource
            ReceivedMessageDetails receivedMessageDetails = new ReceivedMessageDetails();
            receivedMessageDetails.setRxSource(RxSource.NA);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(receivedMessageDetails.toJson());
                object.set(AppContext.RECEIVEDMSGDETAILS_STRING, jsonNode);
            } catch (JsonProcessingException e) {
                logger.error("Exception deserializing MAP message", e);
            }
        }

        OdeMapMetadata metadata = (OdeMapMetadata) JsonUtils.fromJson(metadataNode.toString(), OdeMapMetadata.class);

        if (metadata.getSchemaVersion() <= 4) {
            metadata.setReceivedMessageDetails(null);
        }

        OdeMapPayload payload = new OdeMapPayload(MAPBuilder.genericMAP(consumed.findValue("MapData")));
        return new OdeMapData(metadata, payload);
    }

    public OdeMapData getOdeMapDataFromMessageFrameXml(String xml, long timestamp) throws XmlUtilsException {
        ObjectNode messageFrameNode = XmlUtils.toObjectNode(xml);
        OdeMapMetadata metadata = new OdeMapMetadata();
        metadata.setOdeReceivedAt(Instant.ofEpochMilli(timestamp).toString());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(RecordType.mapTx);
        metadata.setSecurityResultCode(OdeLogMetadata.SecurityResultCode.success);
        var receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.NA);
        metadata.setMapSource(MapSource.RSU);
        OdeMapPayload payload = new OdeMapPayload(MAPBuilder.genericMAP(messageFrameNode.findValue("MapData")));
        return new OdeMapData(metadata, payload);
    }

    public ProcessedMap<LineString> createProcessedMap(OdeMapData odeMap) {
        // Don't validate by default
        return createProcessedMap(odeMap, false);
    }

    public ProcessedMap<LineString> createProcessedMap(OdeMapData odeMap, boolean validate) {

        JsonValidatorResult validationResults =
                validate ? mapJsonValidator.validate(odeMap.toString())
                        : new JsonValidatorResult();

        OdeMapMetadata mapMetadata = (OdeMapMetadata) odeMap.getMetadata();
        OdeMapPayload mapPayload = (OdeMapPayload) odeMap.getPayload();
        J2735IntersectionGeometry intersection = mapPayload.getMap().getIntersections().getIntersections().get(0);

        MapSharedProperties sharedProps = converter.createProperties(mapPayload, mapMetadata, intersection,
                validationResults);
        MapFeatureCollection<LineString> mapFeatureCollection = converter.createFeatureCollection(intersection);
        ConnectingLanesFeatureCollection<LineString> connectingLanesFeatureCollection = converter
                .createConnectingLanesFeatureCollection(mapMetadata, intersection);

        ProcessedMap<LineString> processedMapObject = new ProcessedMap<LineString>();
        processedMapObject.setMapFeatureCollection(mapFeatureCollection);
        processedMapObject.setConnectingLanesFeatureCollection(connectingLanesFeatureCollection);
        processedMapObject.setProperties(sharedProps);

        var key = new RsuIntersectionKey();
        key.setRsuId(mapMetadata.getOriginIp());
        key.setIntersectionReferenceID(intersection.getId());
        return processedMapObject;
    }
}
