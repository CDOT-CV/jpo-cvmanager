package us.dot.its.jpo.snmp.snmp4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.smi.OID;
import us.dot.its.jpo.snmp.client.SnmpBinding;
import us.dot.its.jpo.snmp.client.SnmpException;
import us.dot.its.jpo.snmp.client.SnmpOid;
import us.dot.its.jpo.snmp.client.SnmpValue;
import static us.dot.its.jpo.snmp.client.SnmpException.Kind.*;

/** Validates protocol evidence before a reply can become application data. */
final class Snmp4jResponseValidator {
    private Snmp4jResponseValidator() {}

    static List<SnmpBinding> validate(ScopedPDU request, PDU response) {
        boolean write = request.getType() == PDU.SET;
        validateEnvelope(request, response, write);
        boolean bulk = request.getType() == PDU.GETBULK;
        if (bulk ? response.size() < 1 || response.size() > request.getMaxRepetitions()
                : response.size() != request.size()) throw new SnmpException(INVALID_OUTPUT, write);
        try {
            var bindings = new ArrayList<SnmpBinding>();
            for (var binding : response.getVariableBindings()) {
                bindings.add(new SnmpBinding(new SnmpOid(binding.getOid().toString()),
                        Snmp4jCodec.decode(binding.getVariable())));
            }
            if (bulk) validateBulkOrder(request.get(0).getOid(), bindings);
            else validateRequestedOids(request, bindings);
            return List.copyOf(bindings);
        } catch (SnmpException failure) {
            throw failure.withWriteOutcomeUnknown(write);
        } catch (IllegalArgumentException failure) {
            throw new SnmpException(INVALID_OUTPUT, write);
        }
    }

    private static void validateEnvelope(ScopedPDU request, PDU response, boolean write) {
        if (response.getType() == PDU.REPORT) throw new SnmpException(AUTHENTICATION, false);
        if (!(response instanceof ScopedPDU scoped) || response.getType() != PDU.RESPONSE
                || !Objects.equals(request.getRequestID(), response.getRequestID())
                || !request.getContextName().equals(scoped.getContextName())) throw new SnmpException(INVALID_OUTPUT, write);
        if (response.getErrorStatus() != PDU.noError) {
            int status = response.getErrorStatus();
            // A failed commit/undo is not evidence that the device rolled the write back.
            boolean uncertain = write && (status == PDU.undoFailed || status == PDU.commitFailed || status == PDU.genErr);
            throw new SnmpException(AGENT_REJECTED, uncertain, status, response.getErrorIndex());
        }
    }

    private static void validateRequestedOids(ScopedPDU request, List<SnmpBinding> bindings) {
        for (int i = 0; i < bindings.size(); i++) {
            var binding = bindings.get(i);
            var actual = new OID(binding.oid().value());
            var expected = request.get(i).getOid();
            if (request.getType() == PDU.GETNEXT) {
                if (!(binding.value() instanceof SnmpValue.ExceptionValue) && actual.compareTo(expected) <= 0)
                    throw new SnmpException(INVALID_OUTPUT, false);
            } else if (!actual.equals(expected)) throw new SnmpException(INVALID_OUTPUT, false);
        }
    }

    private static void validateBulkOrder(OID previous, List<SnmpBinding> bindings) {
        boolean ended = false;
        for (var binding : bindings) {
            if (binding.value() instanceof SnmpValue.ExceptionValue exception
                    && exception.kind() == SnmpValue.ExceptionValue.Kind.END_OF_MIB_VIEW) {
                ended = true;
                continue;
            }
            // Repeated end markers are valid; returning data after an end marker is not.
            var actual = new OID(binding.oid().value());
            if (ended || actual.compareTo(previous) <= 0) throw new SnmpException(INVALID_OUTPUT, false);
            previous = actual;
        }
    }
}
