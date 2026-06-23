package us.dot.its.jpo.ode.api.models.systemreports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
public class SystemReport {

    public List<RsuReport> rsuReports = new ArrayList<RsuReport>();
}
