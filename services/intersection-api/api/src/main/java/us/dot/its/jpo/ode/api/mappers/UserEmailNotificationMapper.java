package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserEmailNotification;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserEmailNotificationMapper {

    /**
     * Convert UserEmailNotification entity to UserEmailNotificationDto
     * MapStruct will automatically map fields with the same name
     */
    @Mapping(source = "emailType.emailType", target = "category")
    @Mapping(source = "emailType.description", target = "description")
    @Mapping(source = "emailType.requiredRole.name", target = "requiredRole")
    @Mapping(source = "emailType.supportsImmediate", target = "supportsImmediate")
    @Mapping(source = "emailType.supportsHourly", target = "supportsHourly")
    @Mapping(source = "emailType.supportsDaily", target = "supportsDaily")
    @Mapping(source = "emailType.supportsWeekly", target = "supportsWeekly")
    @Mapping(source = "emailType.supportsMonthly", target = "supportsMonthly")
    UserEmailNotificationDto toDto(UserEmailNotification notification);

    // category, immediate, hourly, daily, weekly, monthly
    @Mapping(source = "emailType", target = "category")
    @Mapping(source = "requiredRole.name", target = "requiredRole")
    @Mapping(target = "immediate", constant = "false")
    @Mapping(target = "hourly", constant = "false")
    @Mapping(target = "daily", constant = "false")
    @Mapping(target = "weekly", constant = "false")
    @Mapping(target = "monthly", constant = "false")
    UserEmailNotificationDto fromEmailType(EmailType emailType);

    // id, user, emailType
    @Mapping(target = "id", ignore = true) // Never set id when creating/updating - it's auto-generated
    @Mapping(target = "user", ignore = true) // set in service layer
    @Mapping(target = "emailType", ignore = true) // set in service layer
    UserEmailNotification toEntity(UserEmailNotificationDto dto);
}