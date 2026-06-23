package us.dot.its.jpo.ode.api.models.systemreports;

public class RsuReceivedMessage<T> {
    String asn1;
    boolean scmsSignatureValid;
    T message;
}
