package us.dot.its.jpo.ode.api.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuOtaRequest;
import us.dot.its.jpo.ode.api.models.storage.ObuOtaRequestPage;
import us.dot.its.jpo.ode.api.repositories.ObuOtaRequestRepository;

/** Searches the OBU firmware download history recorded by the OBU OTA server. */
@Service
@RequiredArgsConstructor
public class ObuOtaRequestService {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    private final ObuOtaRequestRepository repository;

    /** Filters that narrow an OBU OTA request search; null values are not applied. */
    public record Filters(String serial, String manufacturer, String status, Instant start, Instant end) {
        public static Filters none() {
            return new Filters(null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public ObuOtaRequestPage search(int pageNumber, int pageSize, Filters filters, String sort) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        if (filters.start() != null && filters.end() != null && filters.start().isAfter(filters.end())) {
            throw new IllegalArgumentException("start must not be after end");
        }

        var page = repository.findAll(specification(filters), PageRequest.of(pageNumber, pageSize, toSort(sort)));
        var items = page.getContent().stream().map(ObuOtaRequestService::toItem).toList();
        return new ObuOtaRequestPage(items, page.getTotalElements());
    }

    private static Specification<ObuOtaRequest> specification(Filters filters) {
        String serial = trimToNull(filters.serial());
        String manufacturer = trimToNull(filters.manufacturer());
        Boolean errorStatus = parseStatus(filters.status());
        LocalDateTime start = toUtc(filters.start());
        LocalDateTime end = toUtc(filters.end());

        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (serial != null) {
                predicates.add(builder.like(builder.lower(root.get("obuSn")),
                        "%" + escapeLike(serial.toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (manufacturer != null) {
                predicates.add(builder.equal(builder.lower(root.get("manufacturer").get("name")),
                        manufacturer.toLowerCase(Locale.ROOT)));
            }
            if (errorStatus != null) {
                predicates.add(builder.equal(root.get("errorStatus"), errorStatus));
            }
            if (start != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("requestDatetime"), start));
            }
            if (end != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("requestDatetime"), end));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Boolean parseStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case STATUS_SUCCESS -> false;
            case STATUS_ERROR -> true;
            default -> throw new IllegalArgumentException("status must be success or error");
        };
    }

    private static Sort toSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.trim().split(",", 2);
        String field = parts.length == 0 || parts[0].isBlank() ? "request_datetime" : parts[0].trim();
        boolean ascending = parts.length == 2 && "asc".equalsIgnoreCase(parts[1].trim());
        if (parts.length == 2 && !ascending && !"desc".equalsIgnoreCase(parts[1].trim())) {
            throw new IllegalArgumentException("sort direction must be asc or desc");
        }

        String property = switch (field) {
            case "request_datetime" -> "requestDatetime";
            case "obu_sn" -> "obuSn";
            case "requested_firmware_version" -> "requestedFirmwareVersion";
            case "obu_firmware_version" -> "obuFirmwareVersion";
            default -> throw new IllegalArgumentException("sort field is not supported");
        };
        Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Request ids break ties so paging is stable
        return Sort.by(direction, property).and(Sort.by(direction, "requestId"));
    }

    private static ObuOtaRequestPage.Item toItem(ObuOtaRequest request) {
        return new ObuOtaRequestPage.Item(
                request.getRequestId(),
                request.getObuSn(),
                request.getRequestDatetime().toInstant(ZoneOffset.UTC),
                request.getOriginIp() == null ? null : request.getOriginIp().getHostAddress(),
                request.getObuFirmwareVersion(),
                request.getRequestedFirmwareVersion(),
                Boolean.TRUE.equals(request.getErrorStatus()) ? STATUS_ERROR : STATUS_SUCCESS,
                request.getErrorMessage(),
                request.getManufacturer().getName());
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
