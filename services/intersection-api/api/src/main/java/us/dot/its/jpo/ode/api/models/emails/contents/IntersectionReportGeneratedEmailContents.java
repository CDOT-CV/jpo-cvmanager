package us.dot.its.jpo.ode.api.models.emails.contents;

import lombok.Data;
import us.dot.its.jpo.ode.api.models.ReportDocument;

@Data
public class IntersectionReportGeneratedEmailContents {
    private ReportDocument report;
}
