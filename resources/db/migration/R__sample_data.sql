-- R__sample_data.sql
-- Dev seed data for local development and testing only.
-- Do NOT apply to production environments.
-- Flyway re-runs this script whenever its checksum changes.

INSERT INTO public.manufacturers(name)
  VALUES ('Commsignia'), ('Yunex')
  ON CONFLICT (name) DO NOTHING;

INSERT INTO public.rsu_models(name, supported_radio, manufacturer)
  VALUES ('ITS-RS4-M', 'DSRC,C-V2X', 1), ('RSU2X US', 'DSRC,C-V2X', 2)
  ON CONFLICT (name) DO NOTHING;

INSERT INTO public.firmware_images(name, model, install_package, version)
  VALUES ('y20.0.0', 1, 'install_y20_0_0.tar', 'y20.0.0'), ('y20.1.0', 1, 'install_y20_1_0.tar', 'y20.1.0')
  ON CONFLICT (name) DO NOTHING;

INSERT INTO public.firmware_upgrade_rules(from_id, to_id)
  VALUES (1, 2)
  ON CONFLICT DO NOTHING;

INSERT INTO public.organizations(name)
  VALUES ('Test Org'), ('Test Org 2')
  ON CONFLICT (name) DO NOTHING;

INSERT INTO public.rsu_credentials(username, password, nickname, owner_organization_id)
  VALUES ('username', 'password', 'cred1', 1)
  ON CONFLICT (nickname) DO NOTHING;

INSERT INTO public.snmp_credentials(username, password, encrypt_password, nickname, owner_organization_id)
  VALUES ('username', 'password', 'encryption-pw', 'snmp1', 1)
  ON CONFLICT (nickname) DO NOTHING;

INSERT INTO public.snmp_protocols(protocol_code, nickname)
  VALUES ('41', 'RSU 4.1'), ('1218', 'NTCIP 1218')
  ON CONFLICT (nickname) DO NOTHING;

-- RSU 1 and 2 receive telemetry rows below (ping/rsu_health/scms_health), which are
-- RESTRICT-on-delete: they demonstrate that an RSU with telemetry cannot be deleted.
-- RSU 3 is the CASCADE demo: it is given the full set of CASCADE children but NO
-- telemetry, so `DELETE FROM rsus WHERE rsu_id = 3` cascades cleanly.
INSERT INTO public.rsus(geography, milepost, ipv4_address, serial_number, iss_scms_id, primary_route, model, credential_id, snmp_credential_id, snmp_protocol_id, firmware_version, target_firmware_version)
  VALUES
    (ST_GeomFromText('POINT(-105.0135030 39.7405654)'), 1, '10.0.0.180', 'E5672', 'E5672', 'I999', 1, 1, 1, 1, 1, 1),
    (ST_GeomFromText('POINT(-104.987775 39.981805)'), 2, '10.0.0.78', 'E5321', 'E5321', 'I999', 1, 1, 1, 2, 2, 2),
    (ST_GeomFromText('POINT(-105.0908854 39.5880413)'), 3, '10.0.0.79', 'E5999', 'E5999', 'I999', 1, 1, 1, 1, 1, 1)
  ON CONFLICT DO NOTHING;

-- RSU 3 row also gives the partial index idx_rsu_options_tim_deposit (WHERE tim_deposit = true)
-- a second qualifying row.
INSERT INTO public.rsu_options(rsu_id, tim_deposit, snmp_monitoring)
  VALUES (1, TRUE, TRUE), (2, FALSE, TRUE), (3, TRUE, TRUE)
  ON CONFLICT (rsu_id) DO NOTHING;

INSERT INTO public.roles(name)
  VALUES ('admin'), ('operator'), ('user')
  ON CONFLICT (name) DO NOTHING;

-- RSU 3 is placed in Org 2 so that Org 2 exercises all three org-junction CASCADEs at once
-- (rsu_organization + intersection_organization + user_organization) on org delete.
INSERT INTO public.rsu_organization(rsu_id, organization_id)
  VALUES (1, 1), (2, 1), (3, 2)
  ON CONFLICT DO NOTHING;

-- Replace email with a real address to test GCP OAuth2.0 support
INSERT INTO public.users(keycloak_id, email, first_name, last_name, created_timestamp, super_user)
  VALUES ('fc3d8729-8526-4aaa-805b-d64bf3b93860'::UUID, 'test@gmail.com', 'Test', 'User', (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000), '1')
  ON CONFLICT (email) DO NOTHING;

INSERT INTO public.user_organization(user_id, organization_id, role_id)
  VALUES (1, 1, 1), (1, 2, 3)
  ON CONFLICT DO NOTHING;

INSERT INTO public.snmp_msgfwd_type(name)
  VALUES ('rsuDsrcFwd'), ('rsuReceivedMsg'), ('rsuXmitMsgFwding')
  ON CONFLICT (name) DO NOTHING;

INSERT INTO public.snmp_msgfwd_config(rsu_id, msgfwd_type, snmp_index, message_type, dest_ipv4, dest_port, start_datetime, end_datetime, active, security)
  VALUES
    (1, 1, 1, 'BSM', '10.0.0.80', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '0'),
    (1, 1, 2, 'BSM', '10.0.0.81', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '0'),
    (1, 1, 3, 'BSM', '10.0.0.82', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '1'),
    (2, 2, 1, 'BSM', '10.0.0.80', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '1'),
    (2, 2, 2, 'BSM', '10.0.0.81', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '1'),
    (2, 3, 1, 'MAP', '10.0.0.80', 44920, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '1'),
    (2, 3, 2, 'SPAT', '10.0.0.80', 44910, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '0'),
    (3, 1, 1, 'BSM', '10.0.0.80', 46800, '2024/04/01T00:00:00', '2034/04/01T00:00:00', '1', '0')
  ON CONFLICT DO NOTHING;

INSERT INTO public.email_type(email_type, required_role, description, supports_immediate, supports_hourly, supports_daily, supports_weekly, supports_monthly)
  VALUES
    ('Support Requests',                 1, 'Receive support requests from users', true,  false, false, false, false),
    ('Firmware Upgrade Failures',        2, 'Receive automated firmware upgrade failure emails', true,  false, false, false, false),
    ('Daily Message Counts',             3, 'Receive automated daily message count emails', false, false, true, false, false),
    ('Access Requests',                  1, 'Receive organization access requests from users', true,  false, false, false, false),
    ('Intersection Notification Summary',3, 'Receive automated intersection notification summary emails', true,  true,  true,  true,  true),
    ('Critical Error Messages',          2, 'Receive automated critical error message emails', true,  false, false, false, false)
  ON CONFLICT (email_type) DO UPDATE SET
    required_role      = EXCLUDED.required_role,
    description        = EXCLUDED.description,
    supports_immediate = EXCLUDED.supports_immediate,
    supports_hourly    = EXCLUDED.supports_hourly,
    supports_daily     = EXCLUDED.supports_daily,
    supports_weekly    = EXCLUDED.supports_weekly,
    supports_monthly   = EXCLUDED.supports_monthly;

INSERT INTO public.user_email_notification(user_email_notification_id, user_id, email_type_id, immediate, hourly, daily, weekly, monthly)
  VALUES
    (1, 1, 1, true, false, false, false, false),
    (2, 1, 2, true, false, false, false, false),
    (3, 1, 3, false, false, true, false, false),
    (4, 1, 4, true, false, false, false, false),
    (5, 1, 5, true, true, true, true, true),
    (6, 1, 6, true, false, false, false, false)
  ON CONFLICT DO NOTHING;

INSERT INTO public.intersections(intersection_number, ref_pt, intersection_name)
  VALUES (12109, ST_GeomFromText('POINT(-105.0908854 39.5880413)'), 'S Wadsworth & W Columbine Dr')
  ON CONFLICT (intersection_number) DO NOTHING;

-- Intersection 1 is also assigned to Org 2 so deleting Org 2 cascades this row too.
INSERT INTO public.intersection_organization(intersection_id, organization_id)
  VALUES (1, 1), (1, 2)
  ON CONFLICT DO NOTHING;

-- RSU 3 -> intersection 1 so deleting RSU 3 exercises the rsu_intersection CASCADE.
INSERT INTO public.rsu_intersection(rsu_id, intersection_id)
  VALUES (1, 1), (3, 1)
  ON CONFLICT DO NOTHING;

-- ============================================================
-- Integrity-test fixtures (V4 constraints / V5 indexes)
-- ============================================================
-- The rows below populate tables that were previously unseeded, so the V4
-- CASCADE/RESTRICT FK changes and the V5 FK-column indexes can be exercised
-- against real data on a local stack. All inserts stay valid so the repeatable
-- script always migrates green; constraint *violations* are tested by hand.

-- CASCADE child of RSU 3 (rsu_id PK). Cleared when RSU 3 is deleted.
INSERT INTO public.consecutive_firmware_upgrade_failures(rsu_id, consecutive_failures)
  VALUES (3, 2)
  ON CONFLICT (rsu_id) DO NOTHING;

-- CASCADE child of RSU 3. target_firmware_version references firmware_images.firmware_id = 1.
INSERT INTO public.max_retry_limit_reached_instances(rsu_id, reached_at, target_firmware_version)
  VALUES (3, '2026/01/01T00:00:00', 1)
  ON CONFLICT DO NOTHING;

-- Telemetry tables are RESTRICT-on-delete and have serial PKs with no natural unique
-- key, so each block is guarded by NOT EXISTS to stay idempotent across checksum re-runs.
-- Seeded only for RSU 1 and RSU 2, making them the RESTRICT demo (DELETE blocked while
-- these rows exist). RSU 3 is intentionally left telemetry-free.
INSERT INTO public.ping(timestamp, result, rsu_id)
  SELECT ts, res, rid
  FROM (VALUES
    ('2026/06/01T00:00:00'::timestamp, B'1', 1),
    ('2026/06/01T00:05:00'::timestamp, B'1', 1),
    ('2026/06/01T00:10:00'::timestamp, B'0', 1),
    ('2026/06/01T00:00:00'::timestamp, B'1', 2),
    ('2026/06/01T00:05:00'::timestamp, B'0', 2),
    ('2026/06/01T00:10:00'::timestamp, B'1', 2)
  ) AS seed(ts, res, rid)
  WHERE NOT EXISTS (SELECT 1 FROM public.ping);

INSERT INTO public.rsu_health(timestamp, health, rsu_id)
  SELECT ts, hlth, rid
  FROM (VALUES
    ('2026/06/01T00:00:00'::timestamp, 1, 1),
    ('2026/06/01T00:05:00'::timestamp, 1, 1),
    ('2026/06/01T00:10:00'::timestamp, 0, 1),
    ('2026/06/01T00:00:00'::timestamp, 1, 2),
    ('2026/06/01T00:05:00'::timestamp, 0, 2),
    ('2026/06/01T00:10:00'::timestamp, 1, 2)
  ) AS seed(ts, hlth, rid)
  WHERE NOT EXISTS (SELECT 1 FROM public.rsu_health);

INSERT INTO public.scms_health(timestamp, health, expiration, rsu_id)
  SELECT ts, hlth, exp, rid
  FROM (VALUES
    ('2026/06/01T00:00:00'::timestamp, B'1', '2027/06/01T00:00:00'::timestamp, 1),
    ('2026/06/01T00:05:00'::timestamp, B'1', '2027/06/01T00:00:00'::timestamp, 1),
    ('2026/06/01T00:10:00'::timestamp, B'0', '2027/06/01T00:00:00'::timestamp, 1),
    ('2026/06/01T00:00:00'::timestamp, B'1', '2027/06/01T00:00:00'::timestamp, 2),
    ('2026/06/01T00:05:00'::timestamp, B'1', '2027/06/01T00:00:00'::timestamp, 2),
    ('2026/06/01T00:10:00'::timestamp, B'0', '2027/06/01T00:00:00'::timestamp, 2)
  ) AS seed(ts, hlth, exp, rid)
  WHERE NOT EXISTS (SELECT 1 FROM public.scms_health);
