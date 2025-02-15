package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.dot.its.jpo.ode.api.models.messages.SrmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.context.AppContext;
import us.dot.its.jpo.ode.model.*;
import us.dot.its.jpo.ode.model.Asn1Encoding.EncodingRule;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.plugin.j2735.builders.SRMBuilder;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.util.XmlUtils.XmlUtilsException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j

@Component
public class SrmDecoder implements Decoder {

    private final CodecClient codecClient;

    @Autowired
    public SrmDecoder(CodecClient codecClient) {
        this.codecClient = codecClient;
    }

    @Override
    public CompletableFuture<SrmDecodedMessage> decode(EncodedMessage message) {

        // Send String through ASN.1 Decoder to get Decoded XML Data
        CompletableFuture<String> messageFrameXmlFuture = codecClient.decodeSingle(message.getAsn1Message());

        return messageFrameXmlFuture.thenApply(messageFrameXml -> {
            // Convert to Ode Json
            OdeSrmData srm = null;
            try {
                srm = getOdeSrmDataFromMessageFrameXml(messageFrameXml,
                        DecoderManager.getCurrentTimestamp());
            } catch (XmlUtilsException e) {
                throw new RuntimeException(e);
            }

            // build output data structure
            return new SrmDecodedMessage(srm, message.getAsn1Message(), "");
        }).exceptionally(ex -> {
            log.error("Exception decoding SRM message", ex);
            return new SrmDecodedMessage(null, message.getAsn1Message(), ex.getMessage());
        });


    }

    @Override
    public OdeData getAsOdeData(String encodedData) {
        OdeMsgPayload payload = new OdeAsn1Payload(new OdeHexByteArray(encodedData));

        // construct metadata
        OdeSrmMetadata metadata = new OdeSrmMetadata(payload);
        metadata.setOdeReceivedAt(DecoderManager.getCurrentIsoTimestamp());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(RecordType.srmTx);

        Asn1Encoding unsecuredDataEncoding = new Asn1Encoding("unsecuredData", "MessageFrame", EncodingRule.UPER);
        metadata.addEncoding(unsecuredDataEncoding);

        // construct odeData
        return new OdeAsn1Data(metadata, payload);

    }

    public OdeSrmData getOdeSrmDataFromMessageFrameXml(String xml, long timestamp) throws XmlUtilsException {
        ObjectNode messageFrameNode = XmlUtils.toObjectNode(xml);
        OdeSrmMetadata metadata = new OdeSrmMetadata();
        metadata.setOdeReceivedAt(Instant.ofEpochMilli(timestamp).toString());
        metadata.setOriginIp(DecoderManager.getStaticUserOriginIp());
        metadata.setRecordType(RecordType.srmTx);
        var receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.NA);
        metadata.setSrmSource(OdeSrmMetadata.SrmSource.unknown);
        OdeSrmPayload payload = new OdeSrmPayload(
                SRMBuilder.genericSRM(messageFrameNode.findValue("SignalRequestMessage")));
        return new OdeSrmData(metadata, payload);
    }

    @Override
    public OdeSrmData getAsOdeJson(String consumedData) throws XmlUtilsException {
        ObjectNode consumed = XmlUtils.toObjectNode(consumedData);

        JsonNode metadataNode = consumed.findValue(AppContext.METADATA_STRING);
        if (metadataNode instanceof ObjectNode object) {
            // Removing encodings to match ODE behavior
            object.remove(AppContext.ENCODINGS_STRING);

            // Ssm header file does not have a location and use predefined set required
            // RxSource
            ReceivedMessageDetails receivedMessageDetails = new ReceivedMessageDetails();
            receivedMessageDetails.setRxSource(RxSource.NA);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(receivedMessageDetails.toJson());
                object.set(AppContext.RECEIVEDMSGDETAILS_STRING, jsonNode);
            } catch (JsonProcessingException e) {
                log.error("Exception decoding SRM to ODE json", e);
            }
        }

        OdeSrmMetadata metadata = (OdeSrmMetadata) JsonUtils.fromJson(metadataNode.toString(), OdeSrmMetadata.class);

        if (metadata.getSchemaVersion() <= 4) {
            metadata.setReceivedMessageDetails(null);
        }

        OdeSrmPayload payload = new OdeSrmPayload(SRMBuilder.genericSRM(consumed.findValue("SignalRequestMessage")));
        return new OdeSrmData(metadata, payload);
    }

}
