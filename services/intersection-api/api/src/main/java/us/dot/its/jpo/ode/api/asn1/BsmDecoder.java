package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.dot.its.jpo.ode.api.models.messages.BsmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.context.AppContext;
import us.dot.its.jpo.ode.model.*;
import us.dot.its.jpo.ode.model.Asn1Encoding.EncodingRule;
import us.dot.its.jpo.ode.model.OdeBsmMetadata.BsmSource;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.model.OdeLogMetadata.SecurityResultCode;
import us.dot.its.jpo.ode.plugin.j2735.builders.BsmBuilder;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.util.XmlUtils.XmlUtilsException;

import java.time.Instant;


@Slf4j
@Component
public class BsmDecoder implements Decoder {

    @Override
    public DecodedMessage decode(EncodedMessage message) {
        
        // Convert to Ode Data type and Add Metadata
        OdeData data = getAsOdeData(message.getAsn1Message());

        XmlUtils xmlUtils = new XmlUtils();

        try {
            // Convert to XML for ASN.1 Decoder
            String xml = xmlUtils.toXml(data);

            // Send String through ASN.1 Decoder to get Decoded XML Data
            String decodedXml = DecoderManager.decodeXmlWithAcm(xml);

            // Convert to Ode Json 
            OdeBsmData bsm = getAsOdeJson(decodedXml);

            // build output data structure
            DecodedMessage decodedMessage = new BsmDecodedMessage(bsm, message.getAsn1Message(), "");
            return decodedMessage;
            
        } catch (JsonProcessingException e) {
            log.error("JSON error decoding BSM", e);
            return new BsmDecodedMessage(null, message.getAsn1Message(), e.getMessage());
        } catch (Exception e) {
            log.error("General error decoding BSM", e);
            return new BsmDecodedMessage(null, message.getAsn1Message(), e.getMessage());
        }
    }

    @Override
    public OdeData getAsOdeData(String encodedData) {
        OdeMsgPayload payload = new OdeAsn1Payload(new OdeHexByteArray(encodedData));

        // construct metadata
        OdeBsmMetadata metadata = new OdeBsmMetadata(payload);
        metadata.setOdeReceivedAt(DecoderManager.getCurrentIsoTimestamp());
        metadata.setRecordType(RecordType.bsmTx);
        metadata.setSecurityResultCode(SecurityResultCode.success);

        // construct metadata: receivedMessageDetails
        ReceivedMessageDetails receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.RV);

        // construct metadata: locationData
        OdeLogMsgMetadataLocation locationData = new OdeLogMsgMetadataLocation();
        receivedMessageDetails.setLocationData(locationData);

        metadata.setReceivedMessageDetails(receivedMessageDetails);
        metadata.setBsmSource(BsmSource.RV);

        Asn1Encoding unsecuredDataEncoding = new Asn1Encoding("unsecuredData", "MessageFrame",
                EncodingRule.UPER);
        metadata.addEncoding(unsecuredDataEncoding);

        // construct odeData
        return new OdeAsn1Data(metadata, payload);
    }

    public OdeBsmData getOdeBsmDataFromMessageFrameXml(String xml, long timestamp) throws XmlUtilsException {
        ObjectNode messageFrameNode = XmlUtils.toObjectNode(xml);
        OdeBsmMetadata metadata = new OdeBsmMetadata();
        metadata.setOdeReceivedAt(Instant.ofEpochMilli(timestamp).toString());
        metadata.setOriginIp(DecoderManager.getOriginIp());
        metadata.setRecordType(RecordType.bsmTx);
        metadata.setSecurityResultCode(OdeLogMetadata.SecurityResultCode.success);
        var receivedMessageDetails = new ReceivedMessageDetails();
        receivedMessageDetails.setRxSource(RxSource.RV);
        metadata.setBsmSource(BsmSource.RV);
        OdeBsmPayload payload = new OdeBsmPayload(BsmBuilder.genericBsm(messageFrameNode.findValue("BasicSafetyMessage")));
        return new OdeBsmData(metadata, payload);
    }

    @Override
    public OdeBsmData getAsOdeJson(String consumedData) throws XmlUtilsException {
        ObjectNode consumed = XmlUtils.toObjectNode(consumedData);

        JsonNode metadataNode = consumed.findValue(AppContext.METADATA_STRING);
        if (metadataNode instanceof ObjectNode object) {
            // Removing encodings to match ODE behavior
            object.remove(AppContext.ENCODINGS_STRING);
        }
        
        OdeBsmMetadata metadata = (OdeBsmMetadata) JsonUtils.fromJson(
            metadataNode.toString(), OdeBsmMetadata.class);
        
        OdeBsmPayload payload = new OdeBsmPayload(
            BsmBuilder.genericBsm(consumed.findValue("BasicSafetyMessage")));
        return new OdeBsmData(metadata, payload);
    }

}
