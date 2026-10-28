-- V12__per_org_email_subscriptions.sql
-- Scopes organization-specific email subscriptions to a single organization so
-- a user can, for example, receive Daily Message Counts for one of their
-- organizations but not the others. System-wide email types (Critical Error
-- Messages, Support Requests) are not about any one organization and keep a
-- single subscription per user with no organization.
--
-- Steps performed:
--   1. Add email_type.organization_scoped and mark the system-wide types.
--   2. Add user_email_notification.organization_id (nullable; NULL for
--      system-wide types) with a FK to public.organizations.
--   3. Expand every existing organization-scoped subscription into one row per
--      organization the user belongs to, then remove the original
--      organization-less rows for those types. System-wide rows are untouched.
--   4. Replace the (user_id, email_type_id) unique constraint with
--      (user_id, email_type_id, organization_id) NULLS NOT DISTINCT and index
--      organization_id.
--   5. Add prune_user_email_notifications(), which removes a user's
--      subscriptions they no longer hold the email type's required_role for:
--        - organization-scoped: the user must be a member of that organization
--          with at least the required role (super users qualify in every
--          organization they belong to)
--        - system-wide: the user must be a super user or hold at least the
--          required role in any organization
--   6. Run it from triggers on user_organization (role change, membership
--      removal), users (super_user change) and email_type (required_role
--      change). Memberships and roles are changed from several services, so
--      the database owns this cleanup.
--   7. Prune existing subscriptions once so current data follows the same rule.

BEGIN;

-- 1. System-wide email types
ALTER TABLE public.email_type
ADD COLUMN IF NOT EXISTS organization_scoped boolean NOT NULL DEFAULT true;

UPDATE public.email_type
SET organization_scoped = false
WHERE email_type IN ('Critical Error Messages', 'Support Requests');

COMMENT ON COLUMN public.email_type.organization_scoped IS
    'True when subscriptions to this email type are made per organization; false for system-wide email types, '
    'whose subscriptions have no organization.';

-- 2. Subscription organization
ALTER TABLE public.user_email_notification
ADD COLUMN IF NOT EXISTS organization_id integer;

ALTER TABLE public.user_email_notification
ADD CONSTRAINT fk_organization_id FOREIGN KEY (organization_id)
   REFERENCES public.organizations (organization_id) MATCH SIMPLE
   ON UPDATE NO ACTION
   ON DELETE CASCADE;

-- 3. Expand organization-scoped subscriptions. Drop the old unique constraint
-- first so the expanded rows can coexist
ALTER TABLE public.user_email_notification
DROP CONSTRAINT IF EXISTS user_email_notification_unique;

INSERT INTO public.user_email_notification
   (user_id, email_type_id, organization_id, immediate, hourly, daily, weekly, monthly)
SELECT uen.user_id, uen.email_type_id, uo.organization_id,
       uen.immediate, uen.hourly, uen.daily, uen.weekly, uen.monthly
FROM public.user_email_notification uen
JOIN public.email_type et ON et.email_type_id = uen.email_type_id
JOIN public.user_organization uo ON uo.user_id = uen.user_id
WHERE uen.organization_id IS NULL
  AND et.organization_scoped;

DELETE FROM public.user_email_notification uen
USING public.email_type et
WHERE et.email_type_id = uen.email_type_id
  AND et.organization_scoped
  AND uen.organization_id IS NULL;

-- 4. Constraints and indexes
ALTER TABLE public.user_email_notification
ADD CONSTRAINT user_email_notification_unique UNIQUE NULLS NOT DISTINCT (user_id, email_type_id, organization_id);

CREATE INDEX IF NOT EXISTS idx_user_email_notification_organization_id
    ON public.user_email_notification (organization_id);

COMMENT ON INDEX public.idx_user_email_notification_organization_id IS
    'Supports the organization-scoped recipient queries in UserEmailNotificationRepository '
    'and the subscription pruning triggers. FK column; PostgreSQL does not create '
    'indexes on FK columns automatically.';

COMMENT ON COLUMN public.user_email_notification.organization_id IS
    'Organization this subscription applies to. A user subscribes to each organization-scoped email type '
    'separately per organization; NULL for system-wide email types (email_type.organization_scoped = false).';

-- 5. Pruning
-- Ranks role names so they can be compared. Unknown names rank NULL, so a user
-- never qualifies through, or for, a role this function does not know.
CREATE OR REPLACE FUNCTION public.email_role_rank(role_name text)
RETURNS integer AS $$
    SELECT CASE lower(role_name) WHEN 'admin' THEN 3 WHEN 'operator' THEN 2 WHEN 'user' THEN 1 END;
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION public.prune_user_email_notifications(p_user_id integer)
RETURNS void AS $$
    DELETE FROM public.user_email_notification uen
    USING public.users u, public.email_type et, public.roles required_role
    WHERE uen.user_id = p_user_id
      AND u.user_id = uen.user_id
      AND et.email_type_id = uen.email_type_id
      AND required_role.role_id = et.required_role
      AND NOT CASE
          WHEN uen.organization_id IS NULL THEN
              COALESCE(u.super_user = 1::bit, false)
              OR EXISTS (
                  SELECT 1
                  FROM public.user_organization uo
                  JOIN public.roles member_role ON member_role.role_id = uo.role_id
                  WHERE uo.user_id = uen.user_id
                    AND public.email_role_rank(member_role.name) >= public.email_role_rank(required_role.name))
          ELSE
              EXISTS (
                  SELECT 1
                  FROM public.user_organization uo
                  JOIN public.roles member_role ON member_role.role_id = uo.role_id
                  WHERE uo.user_id = uen.user_id
                    AND uo.organization_id = uen.organization_id
                    AND (COALESCE(u.super_user = 1::bit, false)
                         OR public.email_role_rank(member_role.name) >= public.email_role_rank(required_role.name)))
      END;
$$ LANGUAGE sql;

-- 6. Triggers
CREATE OR REPLACE FUNCTION public.prune_email_notifications_on_access_change()
RETURNS trigger AS $$
BEGIN
    PERFORM public.prune_user_email_notifications(OLD.user_id);
    IF TG_OP = 'UPDATE' AND NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        PERFORM public.prune_user_email_notifications(NEW.user_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_organization_prune_email_notifications
AFTER UPDATE OF user_id, organization_id, role_id OR DELETE ON public.user_organization
FOR EACH ROW EXECUTE FUNCTION public.prune_email_notifications_on_access_change();

CREATE TRIGGER trg_users_prune_email_notifications
AFTER UPDATE OF super_user ON public.users
FOR EACH ROW EXECUTE FUNCTION public.prune_email_notifications_on_access_change();

CREATE OR REPLACE FUNCTION public.prune_email_notifications_on_required_role_change()
RETURNS trigger AS $$
BEGIN
    PERFORM public.prune_user_email_notifications(subscribers.user_id)
    FROM (SELECT DISTINCT user_id
          FROM public.user_email_notification
          WHERE email_type_id = NEW.email_type_id) subscribers;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- The WHEN clause skips upserts that rewrite required_role with the same value
CREATE TRIGGER trg_email_type_prune_email_notifications
AFTER UPDATE OF required_role ON public.email_type
FOR EACH ROW WHEN (OLD.required_role IS DISTINCT FROM NEW.required_role)
EXECUTE FUNCTION public.prune_email_notifications_on_required_role_change();

-- 7. Apply the rule to existing subscriptions
SELECT public.prune_user_email_notifications(user_id)
FROM (SELECT DISTINCT user_id FROM public.user_email_notification) subscribers;

COMMIT;
