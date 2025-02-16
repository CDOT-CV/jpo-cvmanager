package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.dot.its.jpo.ode.api.models.messages.SpatDecodedMessage;
import us.dot.its.jpo.geojsonconverter.converter.spat.SpatProcessedJsonConverter;
import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.geojsonconverter.validator.JsonValidatorResult;
import us.dot.its.jpo.geojsonconverter.validator.SpatJsonValidator;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.context.AppContext;
import us.dot.its.jpo.ode.model.*;
import us.dot.its.jpo.ode.model.Asn1Encoding.EncodingRule;
import us.dot.its.jpo.ode.model.OdeSpatMetadata.SpatSource;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.model.OdeLogMetadata.SecurityResultCode;
import us.dot.its.jpo.ode.plugin.j2735.J2735IntersectionState;
import us.dot.its.jpo.ode.plugin.j2735.builders.SPATBuilder;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.util.XmlUtils.XmlUtilsException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j

@Component
public class SpatDecoder implements Decoder {

    private final CodecClient codecClient;

    private final SpatJsonValidator spatJsonValidator;

    @Autowired
    public SpatDecoder(CodecClient codecClient, SpatJsonValidator spatJsonValidator) {
        this.codecClient = codecClient;
        this.spatJsonValidator = spatJsonValidator;
    }


    public SpatProcessedJsonConverter converter = new SpatProcessedJsonConverter();

    @Override
    public CompletableFuture<SpatDecodedMessage> decode(EncodedMessage message) {

        // Send String through ASN.1 Decoder to get Decoded XML Data
        CompletableFuture<String> messageFrameXmlFuture = codecClient.decodeSingle(message.getAsn1Message());

        return messageFrameXmlFuture.thenApply(messageFrameXml -> {
            // Convert to Ode Json
            OdeSpatData spat = null;
            try {
                spat = getOdeSpatDataFromMessageFrameXml(messageFrameXml, DecoderManager.getCurrentTimestamp());
                ProcessedSpat processedSpat = createProcessedSpat(spat);
                return new SpatDecodedMessage(processedSpat, spat, message.getAsn1Message(), "");
            } catch (XmlUtilsException e) {
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            log.error("Exception decoding SPaT message", ex);
            return new SpatDecodedMessage(null, null, message.getAsn1Message(), ex.getMessage());
        });

    }

    @Override
    public OdeData getAsOdeData(String encodedData) {
        OdeMsgPayload payload = new OdeAsn1Payload(new OdeHexByteArray(encodedData));

        // construct metadata
        OdeSpatMetadata metadata = new OdeSpatMetadata(payload);

        metadata.setOdeReceivedAt(DecoderManager.getCurrentIsoTimestamp());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(RecordType.spatTx);
        metadata.setSecurityResultCode(SecurityResultCode.success);

        // construct metadata: receivedMessageDetails
        ReceivedMessageDetails receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.NA);

        // construct metadata: locationData
        OdeLogMsgMetadataLocation locationData = new OdeLogMsgMetadataLocation();
        receivedMessageDetails.setLocationData(locationData);

        metadata.setReceivedMessageDetails(receivedMessageDetails);
        metadata.setSpatSource(SpatSource.V2X);

        Asn1Encoding unsecuredDataEncoding = new Asn1Encoding("unsecuredData", "MessageFrame",
                EncodingRule.UPER);
        metadata.addEncoding(unsecuredDataEncoding);

        // construct odeData
        return new OdeAsn1Data(metadata, payload);
    }

    @Override
    public OdeSpatData getAsOdeJson(String consumedData) throws XmlUtilsException {
        ObjectNode consumed = XmlUtils.toObjectNode(consumedData);

        JsonNode metadataNode = consumed.findValue(AppContext.METADATA_STRING);
        if (metadataNode instanceof ObjectNode object) {
            // Removing encodings to match ODE behavior
            object.remove(AppContext.ENCODINGS_STRING);

            // Spat header file does not have a location and use predefined set required
            // RxSource
            ReceivedMessageDetails receivedMessageDetails = new ReceivedMessageDetails();
            receivedMessageDetails.setRxSource(RxSource.NA);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(receivedMessageDetails.toJson());
                object.set(AppContext.RECEIVEDMSGDETAILS_STRING, jsonNode);
            } catch (JsonProcessingException e) {
                log.error("Exception decoding SPaT to ODE json", e);
            }
        }

        OdeSpatMetadata metadata = (OdeSpatMetadata) JsonUtils.fromJson(metadataNode.toString(), OdeSpatMetadata.class);

        if (metadataNode.findValue("certPresent") != null) {
            boolean isCertPresent = metadataNode.findValue("certPresent").asBoolean();
            metadata.setIsCertPresent(isCertPresent);
        }

        if (metadata.getSchemaVersion() <= 4) {
            metadata.setReceivedMessageDetails(null);
        }

        OdeSpatPayload payload = new OdeSpatPayload(SPATBuilder.genericSPAT(consumed.findValue("SPAT")));
        return new OdeSpatData(metadata, payload);
    }

    public OdeSpatData getOdeSpatDataFromMessageFrameXml(String xml, long timestamp) throws XmlUtilsException {
        ObjectNode messageFrameNode = XmlUtils.toObjectNode(xml);
        OdeSpatMetadata metadata = new OdeSpatMetadata();
        metadata.setOdeReceivedAt(Instant.ofEpochMilli(timestamp).toString());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(OdeLogMetadata.RecordType.spatTx);
        metadata.setSecurityResultCode(OdeLogMetadata.SecurityResultCode.success);
        var receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.NA);
        metadata.setSpatSource(OdeSpatMetadata.SpatSource.V2X);
        OdeSpatPayload payload = new OdeSpatPayload(SPATBuilder.genericSPAT(messageFrameNode.findValue("SPAT")));
        return new OdeSpatData(metadata, payload);
    }

    public ProcessedSpat createProcessedSpat(OdeSpatData odeSpat) {
        // Don't validate by default
        return createProcessedSpat(odeSpat, false);
    }

    public ProcessedSpat createProcessedSpat(OdeSpatData odeSpat, boolean validate) {

        JsonValidatorResult validationResults =
                validate ? spatJsonValidator.validate(odeSpat.toString())
                        : new JsonValidatorResult();

        OdeSpatData rawValue = new OdeSpatData();
        rawValue.setMetadata(odeSpat.getMetadata());
        OdeSpatMetadata spatMetadata = (OdeSpatMetadata) rawValue.getMetadata();

        rawValue.setPayload(odeSpat.getPayload());
        OdeSpatPayload spatPayload = (OdeSpatPayload) rawValue.getPayload();
        J2735IntersectionState intersectionState = spatPayload.getSpat().getIntersectionStateList()
                .getIntersectionStatelist().get(0);

        ProcessedSpat processedSpat = converter.createProcessedSpat(intersectionState, spatMetadata, validationResults);

        var key = new RsuIntersectionKey();
        key.setRsuId(spatMetadata.getOriginIp());
        key.setIntersectionReferenceID(intersectionState.getId());
        return processedSpat;

    }

}
