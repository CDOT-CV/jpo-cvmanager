package us.dot.its.jpo.ode.api.asn1;

import lombok.extern.slf4j.Slf4j;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.ode.api.models.messages.SrmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.model.OdeMsgMetadata.GeneratedBy;
import us.dot.its.jpo.ode.model.OdeLogMetadata.RecordType;
import us.dot.its.jpo.ode.model.OdeLogMetadata.SecurityResultCode;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;
import us.dot.its.jpo.ode.model.OdeMessageFrameMetadata.Source;
import us.dot.its.jpo.ode.model.OdeMessageFramePayload;
import us.dot.its.jpo.ode.model.OdeMessageFrameMetadata;
import us.dot.its.jpo.ode.util.DateTimeUtils;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;

import tools.jackson.databind.DatabindException;
import us.dot.its.jpo.asn.j2735.r2024.SignalRequestMessage.SignalRequestMessageMessageFrame;

/**
 * Decoder implementation for Basic Safety Message (SRM) messages.
 * Converts ASN.1 encoded SRM messages to processed SRM objects.
 */
@Slf4j
@Component
public class SrmDecoder implements Decoder {

    MessageFrameCodec codec;
    public static final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Constructs a SrmDecoder with required dependencies.
     *
     * @param codec MessageFrameCodec for ASN.1 decoding
     */
    SrmDecoder(MessageFrameCodec codec) {
        this.codec = codec;
    }

    /**
     * Decodes an ASN.1 encoded SRM message to a processed SRM object.
     *
     * @param message EncodedMessage containing ASN.1 SRM data
     * @return DecodedMessage containing processed SRM or error details
     */
    @Override
    public DecodedMessage decode(EncodedMessage message) {

        String xer = decodeAsnToXERString(message.getAsn1Message());

        try {
            OdeMessageFrameData odeMessageFrameData = convertXERToMessageFrame(xer);
            return new SrmDecodedMessage(
                    ((SignalRequestMessageMessageFrame) odeMessageFrameData.getPayload().getData()).getValue(),
                    message.getAsn1Message(), "");

        } catch (JacksonException e) {
            return new SrmDecodedMessage(null, message.getAsn1Message(), e.getMessage());
        }

    }

    /**
     * Converts an ASN.1 hex string to XER (XML Encoding Rules) string.
     *
     * @param asnHex ASN.1 encoded hex string
     * @return XER string representation of the message
     */
    @Override
    public String decodeAsnToXERString(String asnHex) {
        byte[] bytes = HexFormat.of().parseHex(asnHex);
        String xer = codec.uperToXer(bytes);
        return xer;
    }

    /**
     * Converts an XER-encoded XML string to an OdeMessageFrameData object.
     *
     * @param encodedXml XER-encoded XML string
     * @return OdeMessageFrameData object
     * @throws DatabindException    if XML mapping fails
     * @throws JacksonException if XML processing fails
     */
    @Override
    public OdeMessageFrameData convertXERToMessageFrame(String encodedXml)
            throws DatabindException, JacksonException {
        OdeMessageFrameMetadata metadata = new OdeMessageFrameMetadata();
        metadata.setOdeReceivedAt(DateTimeUtils.now());
        metadata.setRecordType(RecordType.srmTx);
        metadata.setSecurityResultCode(SecurityResultCode.success);
        metadata.setRecordGeneratedBy(GeneratedBy.OBU);
        metadata.setSource(Source.EV);

        JsonNode rootNode = xmlMapper.readTree(encodedXml);

        MessageFrame<?> messageFrame = xmlMapper.convertValue(rootNode, MessageFrame.class);

        OdeMessageFramePayload payload = new OdeMessageFramePayload(messageFrame);

        return new OdeMessageFrameData(metadata, payload);

    }
}
