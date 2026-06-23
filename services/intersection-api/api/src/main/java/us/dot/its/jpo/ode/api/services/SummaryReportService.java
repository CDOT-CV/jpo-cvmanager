package us.dot.its.jpo.ode.api.services;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import us.dot.its.jpo.conflictmonitor.monitor.models.assessments.LaneDirectionOfTravelAssessment;
import us.dot.its.jpo.conflictmonitor.monitor.models.assessments.LaneDirectionOfTravelAssessmentGroup;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.StopLinePassageEvent;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.StopLineStopEvent;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.minimum_data.MapMinimumDataEvent;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.minimum_data.SpatMinimumDataEvent;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.ode.api.accessors.assessments.lane_direction_of_travel_assessment.LaneDirectionOfTravelAssessmentRepository;
import us.dot.its.jpo.ode.api.accessors.bsm.ProcessedBsmRepository;
import us.dot.its.jpo.ode.api.accessors.events.connection_of_travel_event.ConnectionOfTravelEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.intersection_reference_alignment_event.IntersectionReferenceAlignmentEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.lane_direction_of_travel_event.LaneDirectionOfTravelEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.map_broadcast_rate_event.MapBroadcastRateEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.map_minimum_data_event.MapMinimumDataEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.signal_state_conflict_event.SignalStateConflictEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.spat_broadcast_rate_event.SpatBroadcastRateEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.spat_minimum_data_event.SpatMinimumDataEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.stop_line_passage_event.StopLinePassageEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.stop_line_stop_event.StopLineStopEventRepository;
import us.dot.its.jpo.ode.api.accessors.events.time_change_details_event.TimeChangeDetailsEventRepository;
import us.dot.its.jpo.ode.api.accessors.map.ProcessedMapRepository;

import us.dot.its.jpo.ode.api.accessors.reports.ReportRepository;
import us.dot.its.jpo.ode.api.accessors.spat.ProcessedSpatRepository;
import us.dot.its.jpo.ode.api.models.ConnectionData;
import us.dot.its.jpo.ode.api.models.ConnectionOfTravelData;
import us.dot.its.jpo.ode.api.models.IDCount;
import us.dot.its.jpo.ode.api.models.LaneConnectionCount;
import us.dot.its.jpo.ode.api.models.LaneDirectionOfTravelReportData;
import us.dot.its.jpo.ode.api.models.ReportDocument;
import us.dot.its.jpo.ode.api.models.StopLinePassageReportData;
import us.dot.its.jpo.ode.api.models.StopLineStopReportData;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.systemreports.RsuReport;
import us.dot.its.jpo.ode.api.models.systemreports.SystemReport;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

import us.dot.its.jpo.ode.api.mappers.INetMapper;

@Service
public class SummaryReportService {

    private final ProcessedMapRepository processedMapRepo;
    private final ProcessedSpatRepository processedSpatRepo;
    private final ProcessedBsmRepository processedBsmRepo;
    private final StopLinePassageEventRepository stopLinePassageEventRepo;
    private final StopLineStopEventRepository stopLineStopEventRepo;
    private final ConnectionOfTravelEventRepository connectionOfTravelEventRepo;
    private final IntersectionReferenceAlignmentEventRepository intersectionReferenceAlignmentEventRepo;
    private final LaneDirectionOfTravelEventRepository laneDirectionOfTravelEventRepo;
    private final SignalStateConflictEventRepository signalStateConflictEventRepo;
    private final TimeChangeDetailsEventRepository timeChangeDetailsEventRepo;
    private final LaneDirectionOfTravelAssessmentRepository laneDirectionOfTravelAssessmentRepo;
    private final SpatMinimumDataEventRepository spatMinimumDataEventRepo;
    private final MapMinimumDataEventRepository mapMinimumDataEventRepo;
    private final SpatBroadcastRateEventRepository spatBroadcastRateEventRepo;
    private final MapBroadcastRateEventRepository mapBroadcastRateEventRepo;
    private final int maximumResponseSize;
    private final INetMapper inetMapper;

    private final RsuRepository rsuRepository;

    @Autowired
    public SummaryReportService(
            ProcessedMapRepository processedMapRepo,
            ProcessedSpatRepository processedSpatRepo,
            ProcessedBsmRepository processedBsmRepo,
            StopLinePassageEventRepository stopLinePassageEventRepo,
            StopLineStopEventRepository stopLineStopEventRepo,
            ConnectionOfTravelEventRepository connectionOfTravelEventRepo,
            IntersectionReferenceAlignmentEventRepository intersectionReferenceAlignmentEventRepo,
            LaneDirectionOfTravelEventRepository laneDirectionOfTravelEventRepo,
            SignalStateConflictEventRepository signalStateConflictEventRepo,
            TimeChangeDetailsEventRepository timeChangeDetailsEventRepo,
            LaneDirectionOfTravelAssessmentRepository laneDirectionOfTravelAssessmentRepo,
            SpatMinimumDataEventRepository spatMinimumDataEventRepo,
            MapMinimumDataEventRepository mapMinimumDataEventRepo,
            SpatBroadcastRateEventRepository spatBroadcastRateEventRepo,
            MapBroadcastRateEventRepository mapBroadcastRateEventRepo,
            RsuRepository rsuRepository,
            INetMapper inetMapper,
            @Value("${maximumResponseSize}") int maximumResponseSize) {
        this.processedMapRepo = processedMapRepo;
        this.processedSpatRepo = processedSpatRepo;
        this.processedBsmRepo = processedBsmRepo;
        this.stopLinePassageEventRepo = stopLinePassageEventRepo;
        this.stopLineStopEventRepo = stopLineStopEventRepo;
        this.connectionOfTravelEventRepo = connectionOfTravelEventRepo;
        this.intersectionReferenceAlignmentEventRepo = intersectionReferenceAlignmentEventRepo;
        this.laneDirectionOfTravelEventRepo = laneDirectionOfTravelEventRepo;
        this.signalStateConflictEventRepo = signalStateConflictEventRepo;
        this.timeChangeDetailsEventRepo = timeChangeDetailsEventRepo;
        this.laneDirectionOfTravelAssessmentRepo = laneDirectionOfTravelAssessmentRepo;
        this.spatMinimumDataEventRepo = spatMinimumDataEventRepo;
        this.mapMinimumDataEventRepo = mapMinimumDataEventRepo;
        this.spatBroadcastRateEventRepo = spatBroadcastRateEventRepo;
        this.mapBroadcastRateEventRepo = mapBroadcastRateEventRepo;
        this.rsuRepository = rsuRepository;
        this.inetMapper = inetMapper;
        this.maximumResponseSize = maximumResponseSize;
    }

    public SystemReport buildReport(long startTime, long endTime) {

        List<String> allRsuIps = rsuRepository.findAll().stream()
                .map(rsu -> inetMapper.mapInetAddressToString(rsu.getIpv4Address()))
                .collect(Collectors.toList());

        SystemReport report = new SystemReport();

        for (String rsuIp : allRsuIps) {
            report.rsuReports.add(getRsuReport(rsuIp, startTime, endTime));
        }

        return report;

    }

    public RsuReport getRsuReport(String rsuIp, long startTime, long endTime) {
        RsuReport rsuReport = new RsuReport();

        // Populate the RsuReport with data for the given rsuIp
        rsuReport.setBsmMessageCount(getBsmCounts(rsuIp, startTime, endTime));
        rsuReport.setMapMessageCount(getMapCounts(rsuIp, startTime, endTime));
        rsuReport.setSpatMessageCount(getSpatCounts(rsuIp, startTime, endTime));
        return rsuReport;
    }

    public long getBsmCounts(String rsuIp, long startTime, long endTime) {
        return this.processedBsmRepo.count(rsuIp, rsuIp, startTime, endTime, null, null, null);
    }

    public long getMapCounts(String rsuIp, long startTime, long endTime) {
        return this.processedMapRepo.countByRsuIp(rsuIp, startTime, endTime);
    }

    public long getSpatCounts(String rsuIp, long startTime, long endTime) {
        return this.processedSpatRepo.countByRsuIp(rsuIp, startTime, endTime);
    }

}
