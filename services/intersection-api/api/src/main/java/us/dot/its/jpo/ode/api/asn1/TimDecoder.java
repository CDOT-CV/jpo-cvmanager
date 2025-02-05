package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import us.dot.its.jpo.ode.api.models.messages.TimDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.model.*;
import us.dot.its.jpo.ode.model.Asn1Encoding.EncodingRule;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.model.OdeMsgMetadata.GeneratedBy;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.util.XmlUtils.XmlUtilsException;

@Slf4j
@Component
public class TimDecoder implements Decoder {

    private final CodecClient codecClient;

    private final XmlUtils xmlUtils = new XmlUtils();

    @Autowired
    public TimDecoder(CodecClient codecClient) {
        this.codecClient = codecClient;
    }

    @Override
    public DecodedMessage decode(EncodedMessage message) {
        try {
            // Send String through ASN.1 Decoder to get Decoded XML Data
            String messageFrameXml = codecClient.decodeSingle(message.getAsn1Message());

            // Convert to Ode Data type and Add Metadata
            OdeData data = getAsOdeData(message.getAsn1Message());

            // Convert to Ode Json 
            ObjectNode tim = XmlUtils.toObjectNode(messageFrameXml);

            // build output data structure
            return new TimDecodedMessage(tim, message.getAsn1Message(), "");
            
        } catch (Exception e) {
            log.error("Generic Exception: {}", e.getMessage(), e);
            return new TimDecodedMessage(null, message.getAsn1Message(), e.getMessage());
        }
    }

    @Override
    public OdeData getAsOdeData(String encodedData) {
        OdeMsgPayload payload = new OdeAsn1Payload(new OdeHexByteArray(encodedData));

        // construct metadata
        OdeTimMetadata metadata = new OdeTimMetadata(payload);
        metadata.setOdeReceivedAt(DecoderManager.getCurrentIsoTimestamp());
        metadata.setOriginIp("user-upload");
        metadata.setRecordType(RecordType.timMsg);
        metadata.setRecordGeneratedBy(GeneratedBy.RSU);
        
        Asn1Encoding unsecuredDataEncoding = new Asn1Encoding("unsecuredData", "MessageFrame",EncodingRule.UPER);
        metadata.addEncoding(unsecuredDataEncoding);
        
        //construct odeData
        return new OdeAsn1Data(metadata, payload);
    }



    @Override
    public OdeTimData getAsOdeJson(String consumedData) throws XmlUtilsException {
        // There is no proper deserializer for TIM data into the ODE tim format. This function is not used here.
        return null;
    }
}
