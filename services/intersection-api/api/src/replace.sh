find . -type f -name "*.java" -exec sed -i 's/accessors\.assessments\.ConnectionOfTravelAssessment/accessors\.assessments\.connection_of_travel_assessment/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.assessments\.LaneDirectionOfTravelAssessment/accessors\.assessments\.lane_direction_of_travel_assessment/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.assessments\.SignalStateAssessment/accessors\.assessments\.signal_state_assessment/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.assessments\.SignalStateEventAssessment/accessors\.assessments\.signal_state_event_assessment/g' {} +

find . -type f -name "*.java" -exec sed -i 's/accessors\.config\.DefaultConfig/accessors\.config\.default_config/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.config\.IntersectionConfig/accessors\.config\.intersection_config/g' {} +

find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.BsmEvent/accessors\.events\.bsm_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.BsmMessageCountProgressionEventRepository/accessors\.events\.bsm_message_count_progression_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.ConnectionOfTravelEvent/accessors\.events\.connection_of_travel_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.IntersectionReferenceAlignmentEvent/accessors\.events\.intersection_reference_alignment_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.LaneDirectionOfTravelEvent/accessors\.events\.lane_direction_of_travel_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.MapBroadcastRateEvents/accessors\.events\.map_broadcast_rate_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.MapMessageCountProgressionEventRepository/accessors\.events\.map_message_count_progression_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.MapMinimumDataEvent/accessors\.events\.map_minimum_data_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SignalGroupAlignmentEvent/accessors\.events\.signal_group_alignment_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SignalStateConflictEvent/accessors\.events\.signal_state_conflict_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SignalStateEvent/accessors\.events\.signal_state_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SignalStateStopEvent/accessors\.events\.signal_state_stop_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SpatBroadcastRateEvent/accessors\.events\.spat_broadcast_rate_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SpatMessageCountProgressionEvent/accessors\.events\.spat_message_count_progression_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.SpatMinimumDataEvent/accessors\.events\.spat_minimum_data_event/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.events\.TimeChangeDetailsEvent/accessors\.events\.time_change_details_event/g' {} +

find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.ActiveNotification/accessors\.notifications\.active_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.ConnectionOfTravelNotification/accessors\.notifications\.connection_of_travel_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.IntersectionReferenceAlignmentNotification/accessors\.notifications\.intersection_reference_alignment_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.LaneDirectionOfTravelNotificationRepo/accessors\.notifications\.lane_direction_of_travel_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.MapBroadcastRateNotification/accessors\.notifications\.map_broadcast_rate_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.SignalGroupAlignmentNotificationRepo/accessors\.notifications\.signal_group_alignment_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.SignalStateConflictNotification/accessors\.notifications\.signal_state_conflict_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.SpatBroadcastRateNotification/accessors\.notifications\.spat_broadcast_rate_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.StopLinePassageNotification/accessors\.notifications\.stop_line_passage_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.StopLineStopNotification/accessors\.notifications\.stop_line_stop_notification/g' {} +
find . -type f -name "*.java" -exec sed -i 's/accessors\.notifications\.TimeChangeDetailsNotification/accessors\.notifications\.time_change_details_notification/g' {} +
