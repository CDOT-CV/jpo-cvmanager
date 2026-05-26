-- V202605261242__service_pattern_indexes.sql
--
-- Schema integrity review -- index optimization pass (round 2).
--
-- Six new indexes, each derived from an active query pattern found in the Spring
-- Data repository layer. PostgreSQL does not create indexes on FK columns
-- automatically (unlike MySQL). Several FK columns are used as leading WHERE or
-- JOIN ON predicates and were missing indexes entirely.
--
-- 1. user_organization(organization_id)
--    The UNIQUE constraint added in V202605221641 covers (user_id, organization_id),
--    which supports user-first lookups. UserOrganizationRepository has two methods
--    that start from the organization side: findByOrganization_Name and
--    findByUserAndOrganization_Name. Without an org-first index those queries require
--    a full sequential scan of user_organization, filtered via a join to organizations.
--
-- 2. user_email_notification(email_type_id)
--    All three bulk notification queries in UserEmailNotificationRepository filter
--    by email_type_id as the first predicate. The FK column has no index; each
--    query is currently a full sequential scan of the entire notification table.
--
-- 3. firmware_upgrade_rules(from_id)
--    FirmwareUpgradeRuleRepository.findFirstByFrom_Id resolves the upgrade path for
--    a given firmware version. The FK column from_id has no index; every upgrade
--    path lookup is a full sequential scan of firmware_upgrade_rules.
--
-- 4. rsu_options(rsu_id) WHERE tim_deposit = true
--    The rsu-info-bridge RsuRepository.findByRsuOptionTimDepositIsTrue loads all
--    RSUs that have TIM deposit enabled. Without an index the query scans all rows
--    in rsu_options. A partial index covering only the rows where tim_deposit = true
--    keeps the index small and directly matches the query predicate.
--
-- 5. rsus(primary_route)
--    RsuRepository executes a SELECT DISTINCT primary_route FROM rsus ORDER BY
--    primary_route ASC to populate the primary route dropdown. Without an index this
--    requires a full table scan followed by an in-memory sort. An index on
--    primary_route allows PostgreSQL to read distinct values directly from the index
--    in sorted order.
--
-- 6. rsu_intersection(intersection_id)
--    The UNIQUE constraint covers (rsu_id, intersection_id), which is rsu-first.
--    RsuIntersectionRepository has multiple queries and DELETE operations that start
--    from the intersection side (by intersection_number, which resolves to
--    intersection_id). Without an intersection-first index those operations scan all
--    rows in rsu_intersection for every intersection-based lookup or deletion.

BEGIN;

-- ============================================================
-- 1. user_organization: index for org-first lookups
-- ============================================================

CREATE INDEX idx_user_organization_organization_id
    ON public.user_organization (organization_id);

COMMENT ON INDEX public.idx_user_organization_organization_id IS
    'Supports org-first lookups in UserOrganizationRepository: findByOrganization_Name '
    'and findByUserAndOrganization_Name. The UNIQUE constraint (user_id, organization_id) '
    'is user-first and does not cover these query patterns.';

-- ============================================================
-- 2. user_email_notification: index on FK email_type_id
-- ============================================================

CREATE INDEX idx_user_email_notification_email_type_id
    ON public.user_email_notification (email_type_id);

COMMENT ON INDEX public.idx_user_email_notification_email_type_id IS
    'Supports all three bulk notification recipient queries in UserEmailNotificationRepository, '
    'each of which filters by email_type_id as the leading predicate. FK column; '
    'PostgreSQL does not create indexes on FK columns automatically.';

-- ============================================================
-- 3. firmware_upgrade_rules: index on FK from_id
-- ============================================================

CREATE INDEX idx_firmware_upgrade_rules_from_id
    ON public.firmware_upgrade_rules (from_id);

COMMENT ON INDEX public.idx_firmware_upgrade_rules_from_id IS
    'Supports FirmwareUpgradeRuleRepository.findFirstByFrom_Id, which resolves the '
    'allowed upgrade path for a given firmware version. FK column without index '
    'causes a full sequential scan on every upgrade path resolution.';

-- ============================================================
-- 4. rsu_options: partial index for TIM deposit filter
-- ============================================================

CREATE INDEX idx_rsu_options_tim_deposit
    ON public.rsu_options (rsu_id)
    WHERE tim_deposit = true;

COMMENT ON INDEX public.idx_rsu_options_tim_deposit IS
    'Partial index covering only rows where tim_deposit = true. '
    'Supports rsu-info-bridge RsuRepository.findByRsuOptionTimDepositIsTrue, which '
    'enumerates all TIM-deposit-enabled RSUs. The partial index is deliberately '
    'small: only RSUs with the flag set are indexed.';

-- ============================================================
-- 5. rsus: index on primary_route for distinct-values query
-- ============================================================

CREATE INDEX idx_rsus_primary_route
    ON public.rsus (primary_route);

COMMENT ON INDEX public.idx_rsus_primary_route IS
    'Supports SELECT DISTINCT primary_route FROM rsus ORDER BY primary_route ASC '
    'used by RsuRepository to populate the primary route dropdown. Without this '
    'index the query is a full table scan with an in-memory sort. With the index '
    'PostgreSQL can walk it in order and return distinct values directly.';

-- ============================================================
-- 6. rsu_intersection: index for intersection-first lookups
-- ============================================================

CREATE INDEX idx_rsu_intersection_intersection_id
    ON public.rsu_intersection (intersection_id);

COMMENT ON INDEX public.idx_rsu_intersection_intersection_id IS
    'Supports intersection-first queries and DELETE operations in '
    'RsuIntersectionRepository (e.g. deleteByIntersection_IntersectionNumber, '
    'DELETE WHERE intersection.intersectionNumber = ? AND rsu.ipv4Address IN (...)). '
    'The UNIQUE constraint (rsu_id, intersection_id) is rsu-first and does not '
    'cover these patterns. FK column; PostgreSQL does not index FK columns automatically.';

COMMIT;
