CREATE TABLE public.consecutive_firmware_upgrade_failures (
  consecutive_failures integer NOT NULL,
  rsu_id integer NOT NULL,
  CONSTRAINT consecutive_firmware_upgrade_failures_pkey PRIMARY KEY (rsu_id),
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id)
);

CREATE TABLE public.email_type (
  description character varying(256),
  email_type character varying(128) NOT NULL,
  email_type_id integer DEFAULT nextval('email_type_email_type_id_seq'::regclass) NOT NULL,
  supports_daily boolean DEFAULT false NOT NULL,
  supports_hourly boolean DEFAULT false NOT NULL,
  supports_immediate boolean DEFAULT true NOT NULL,
  supports_monthly boolean DEFAULT false NOT NULL,
  supports_weekly boolean DEFAULT false NOT NULL,
  CONSTRAINT at_least_one_frequency CHECK ((supports_immediate OR supports_hourly OR supports_daily OR supports_weekly OR supports_monthly)),
  CONSTRAINT email_type_pkey PRIMARY KEY (email_type_id),
  CONSTRAINT email_type_unique UNIQUE (email_type)
);

CREATE TABLE public.firmware_images (
  firmware_id integer DEFAULT nextval('firmware_images_firmware_id_seq'::regclass) NOT NULL,
  install_package character varying(128) NOT NULL,
  model integer NOT NULL,
  name character varying(128) NOT NULL,
  version character varying(128) NOT NULL,
  CONSTRAINT firmware_images_install_package UNIQUE (install_package),
  CONSTRAINT firmware_images_name UNIQUE (name),
  CONSTRAINT firmware_images_pkey PRIMARY KEY (firmware_id),
  CONSTRAINT firmware_images_version UNIQUE (version),
  CONSTRAINT fk_model FOREIGN KEY (model) REFERENCES rsu_models(rsu_model_id)
);

CREATE TABLE public.firmware_upgrade_rules (
  firmware_upgrade_rule_id integer DEFAULT nextval('firmware_upgrade_rules_firmware_upgrade_rule_id_seq'::regclass) NOT NULL,
  from_id integer NOT NULL,
  to_id integer NOT NULL,
  CONSTRAINT firmware_upgrade_rules_pkey PRIMARY KEY (firmware_upgrade_rule_id),
  CONSTRAINT fk_from_id FOREIGN KEY (from_id) REFERENCES firmware_images(firmware_id),
  CONSTRAINT fk_to_id FOREIGN KEY (to_id) REFERENCES firmware_images(firmware_id)
);

CREATE TABLE public.flyway_schema_history (
  checksum integer,
  description character varying(200) NOT NULL,
  execution_time integer NOT NULL,
  installed_by character varying(100) NOT NULL,
  installed_on timestamp without time zone DEFAULT now() NOT NULL,
  installed_rank integer NOT NULL,
  script character varying(1000) NOT NULL,
  success boolean NOT NULL,
  type character varying(20) NOT NULL,
  version character varying(50),
  CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);

CREATE TABLE public.intersection_organization (
  intersection_id integer NOT NULL,
  intersection_organization_id integer DEFAULT nextval('intersection_organization_intersection_organization_id_seq'::regclass) NOT NULL,
  organization_id integer NOT NULL,
  CONSTRAINT fk_intersection_id FOREIGN KEY (intersection_id) REFERENCES intersections(intersection_id),
  CONSTRAINT fk_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(organization_id),
  CONSTRAINT intersection_organization_pkey PRIMARY KEY (intersection_organization_id)
);

CREATE TABLE public.intersections (
  bbox geography(Polygon,4326),
  intersection_id integer DEFAULT nextval('intersections_intersection_id_seq'::regclass) NOT NULL,
  intersection_name character varying(128),
  intersection_number character varying(128) NOT NULL,
  origin_ip inet,
  ref_pt geography(Point,4326) NOT NULL,
  CONSTRAINT intersection_intersection_number UNIQUE (intersection_number),
  CONSTRAINT intersection_pkey PRIMARY KEY (intersection_id)
);

CREATE TABLE public.iss_keys (
  common_name character varying(128) NOT NULL,
  iss_key_id integer DEFAULT nextval('iss_keys_iss_key_id_seq'::regclass) NOT NULL,
  token character varying(128) NOT NULL,
  CONSTRAINT iss_keys_pkey PRIMARY KEY (iss_key_id)
);

CREATE TABLE public.manufacturers (
  manufacturer_id integer DEFAULT nextval('manufacturers_manufacturer_id_seq'::regclass) NOT NULL,
  name character varying(128) NOT NULL,
  CONSTRAINT manufacturers_name UNIQUE (name),
  CONSTRAINT manufacturers_pkey PRIMARY KEY (manufacturer_id)
);

CREATE TABLE public.max_retry_limit_reached_instances (
  reached_at timestamp without time zone NOT NULL,
  rsu_id integer NOT NULL,
  target_firmware_version integer NOT NULL,
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT fk_target_firmware_version FOREIGN KEY (target_firmware_version) REFERENCES firmware_images(firmware_id),
  CONSTRAINT max_retry_limit_reached_instances_pkey PRIMARY KEY (rsu_id, reached_at)
);

CREATE TABLE public.obu_ota_requests (
  error_message character varying(128) NOT NULL,
  error_status bit(1) NOT NULL,
  manufacturer integer NOT NULL,
  obu_firmware_version character varying(128) NOT NULL,
  obu_sn character varying(128) NOT NULL,
  origin_ip inet NOT NULL,
  request_datetime timestamp without time zone NOT NULL,
  request_id integer DEFAULT nextval('obu_ota_request_id_seq'::regclass) NOT NULL,
  requested_firmware_version character varying(128) NOT NULL,
  CONSTRAINT fk_manufacturer FOREIGN KEY (manufacturer) REFERENCES manufacturers(manufacturer_id),
  CONSTRAINT obu_ota_requests_pkey PRIMARY KEY (request_id)
);

CREATE TABLE public.organizations (
  email character varying(128),
  name character varying(128) NOT NULL,
  organization_id integer DEFAULT nextval('organizations_organization_id_seq'::regclass) NOT NULL,
  CONSTRAINT organizations_name UNIQUE (name),
  CONSTRAINT organizations_pkey PRIMARY KEY (organization_id)
);

CREATE TABLE public.ping (
  ping_id integer DEFAULT nextval('ping_ping_id_seq'::regclass) NOT NULL,
  result bit(1) NOT NULL,
  rsu_id integer NOT NULL,
  "timestamp" timestamp without time zone NOT NULL,
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT ping_pkey PRIMARY KEY (ping_id)
);

CREATE TABLE public.roles (
  name character varying(128) NOT NULL,
  role_id integer DEFAULT nextval('roles_role_id_seq'::regclass) NOT NULL,
  CONSTRAINT roles_name UNIQUE (name),
  CONSTRAINT roles_pkey PRIMARY KEY (role_id)
);

CREATE TABLE public.rsu_credentials (
  credential_id integer DEFAULT nextval('rsu_credentials_credential_id_seq'::regclass) NOT NULL,
  nickname character varying(128) NOT NULL,
  owner_organization_id integer NOT NULL,
  password character varying(128) NOT NULL,
  username character varying(128) NOT NULL,
  CONSTRAINT fk_rsu_credential_owner_organization_id FOREIGN KEY (owner_organization_id) REFERENCES organizations(organization_id),
  CONSTRAINT rsu_credentials_nickname UNIQUE (nickname),
  CONSTRAINT rsu_credentials_pkey PRIMARY KEY (credential_id)
);

CREATE TABLE public.rsu_health (
  health integer NOT NULL,
  rsu_health_id integer DEFAULT nextval('rsu_health_rsu_health_id_seq'::regclass) NOT NULL,
  rsu_id integer NOT NULL,
  "timestamp" timestamp without time zone NOT NULL,
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT rsu_health_pkey PRIMARY KEY (rsu_health_id)
);

CREATE TABLE public.rsu_intersection (
  intersection_id integer NOT NULL,
  rsu_id integer NOT NULL,
  rsu_intersection_id integer DEFAULT nextval('rsu_intersection_rsu_intersection_id_seq'::regclass) NOT NULL,
  CONSTRAINT fk_intersection_id FOREIGN KEY (intersection_id) REFERENCES intersections(intersection_id),
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT rsu_intersection_pkey PRIMARY KEY (rsu_intersection_id),
  CONSTRAINT rsu_intersection_unique UNIQUE (rsu_id, intersection_id)
);

CREATE TABLE public.rsu_models (
  manufacturer integer NOT NULL,
  name character varying(128) NOT NULL,
  rsu_model_id integer DEFAULT nextval('rsu_models_rsu_model_id_seq'::regclass) NOT NULL,
  supported_radio character varying(128) NOT NULL,
  CONSTRAINT fk_manufacturer FOREIGN KEY (manufacturer) REFERENCES manufacturers(manufacturer_id),
  CONSTRAINT rsu_models_name UNIQUE (name),
  CONSTRAINT rsu_models_pkey PRIMARY KEY (rsu_model_id)
);

CREATE TABLE public.rsu_options (
  rsu_id integer NOT NULL,
  snmp_monitoring boolean DEFAULT false NOT NULL,
  tim_deposit boolean DEFAULT false NOT NULL,
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT rsu_options_pkey PRIMARY KEY (rsu_id)
);

CREATE TABLE public.rsu_organization (
  organization_id integer NOT NULL,
  rsu_id integer NOT NULL,
  rsu_organization_id integer DEFAULT nextval('rsu_organization_rsu_organization_id_seq'::regclass) NOT NULL,
  CONSTRAINT fk_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(organization_id),
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT rsu_organization_pkey PRIMARY KEY (rsu_organization_id)
);

CREATE TABLE public.rsus (
  credential_id integer NOT NULL,
  firmware_version integer,
  geography geography NOT NULL,
  ipv4_address inet NOT NULL,
  iss_scms_id character varying(128) NOT NULL,
  milepost double precision NOT NULL,
  model integer NOT NULL,
  primary_route character varying(128) NOT NULL,
  rsu_id integer DEFAULT nextval('rsus_rsu_id_seq'::regclass) NOT NULL,
  serial_number character varying(128) NOT NULL,
  snmp_credential_id integer NOT NULL,
  snmp_protocol_id integer NOT NULL,
  target_firmware_version integer,
  CONSTRAINT fk_credential_id FOREIGN KEY (credential_id) REFERENCES rsu_credentials(credential_id),
  CONSTRAINT fk_firmware_version FOREIGN KEY (firmware_version) REFERENCES firmware_images(firmware_id),
  CONSTRAINT fk_model FOREIGN KEY (model) REFERENCES rsu_models(rsu_model_id),
  CONSTRAINT fk_snmp_credential_id FOREIGN KEY (snmp_credential_id) REFERENCES snmp_credentials(snmp_credential_id),
  CONSTRAINT fk_snmp_protocol_id FOREIGN KEY (snmp_protocol_id) REFERENCES snmp_protocols(snmp_protocol_id),
  CONSTRAINT fk_target_firmware_version FOREIGN KEY (target_firmware_version) REFERENCES firmware_images(firmware_id),
  CONSTRAINT rsu_ipv4_address UNIQUE (ipv4_address),
  CONSTRAINT rsu_iss_scms_id UNIQUE (iss_scms_id),
  CONSTRAINT rsu_milepost_primary_route UNIQUE (milepost, primary_route),
  CONSTRAINT rsu_pkey PRIMARY KEY (rsu_id),
  CONSTRAINT rsu_serial_number UNIQUE (serial_number)
);

CREATE TABLE public.scms_health (
  expiration timestamp without time zone,
  health bit(1) NOT NULL,
  rsu_id integer NOT NULL,
  scms_health_id integer DEFAULT nextval('scms_health_scms_health_id_seq'::regclass) NOT NULL,
  "timestamp" timestamp without time zone NOT NULL,
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT scms_health_pkey PRIMARY KEY (scms_health_id)
);

CREATE TABLE public.snmp_credentials (
  encrypt_password character varying(128),
  nickname character varying(128) NOT NULL,
  owner_organization_id integer NOT NULL,
  password character varying(128) NOT NULL,
  snmp_credential_id integer DEFAULT nextval('snmp_credentials_snmp_credential_id_seq'::regclass) NOT NULL,
  username character varying(128) NOT NULL,
  CONSTRAINT fk_snmp_credential_owner_organization_id FOREIGN KEY (owner_organization_id) REFERENCES organizations(organization_id),
  CONSTRAINT snmp_credentials_nickname UNIQUE (nickname),
  CONSTRAINT snmp_credentials_pkey PRIMARY KEY (snmp_credential_id)
);

CREATE TABLE public.snmp_msgfwd_config (
  active bit(1) NOT NULL,
  dest_ipv4 inet NOT NULL,
  dest_port integer NOT NULL,
  end_datetime timestamp without time zone NOT NULL,
  message_type character varying(128) NOT NULL,
  msgfwd_type integer NOT NULL,
  rsu_id integer NOT NULL,
  security bit(1) NOT NULL,
  snmp_index integer NOT NULL,
  start_datetime timestamp without time zone NOT NULL,
  CONSTRAINT fk_msgfwd_type FOREIGN KEY (msgfwd_type) REFERENCES snmp_msgfwd_type(snmp_msgfwd_type_id),
  CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id) REFERENCES rsus(rsu_id),
  CONSTRAINT snmp_msgfwd_config_pkey PRIMARY KEY (rsu_id, msgfwd_type, snmp_index)
);

CREATE TABLE public.snmp_msgfwd_type (
  name character varying(128) NOT NULL,
  snmp_msgfwd_type_id integer DEFAULT nextval('snmp_msgfwd_type_id_seq'::regclass) NOT NULL,
  CONSTRAINT snmp_msgfwd_type_name UNIQUE (name),
  CONSTRAINT snmp_msgfwd_type_pkey PRIMARY KEY (snmp_msgfwd_type_id)
);

CREATE TABLE public.snmp_protocols (
  nickname character varying(128) NOT NULL,
  protocol_code character varying(128) NOT NULL,
  snmp_protocol_id integer DEFAULT nextval('snmp_protocols_snmp_protocol_id_seq'::regclass) NOT NULL,
  CONSTRAINT snmp_protocols_nickname UNIQUE (nickname),
  CONSTRAINT snmp_protocols_pkey PRIMARY KEY (snmp_protocol_id)
);

CREATE TABLE public.spatial_ref_sys (
  auth_name character varying(256),
  auth_srid integer,
  proj4text character varying(2048),
  srid integer NOT NULL,
  srtext character varying(2048),
  CONSTRAINT spatial_ref_sys_pkey PRIMARY KEY (srid),
  CONSTRAINT spatial_ref_sys_srid_check CHECK (((srid > 0) AND (srid <= 998999)))
);

CREATE TABLE public.user_email_notification (
  daily boolean DEFAULT false NOT NULL,
  email_type_id integer NOT NULL,
  hourly boolean DEFAULT false NOT NULL,
  immediate boolean DEFAULT true NOT NULL,
  monthly boolean DEFAULT false NOT NULL,
  user_email_notification_id integer DEFAULT nextval('user_email_notification_user_email_notification_id_seq'::regclass) NOT NULL,
  user_id integer NOT NULL,
  weekly boolean DEFAULT false NOT NULL,
  CONSTRAINT at_least_one_subscription CHECK ((immediate OR hourly OR daily OR weekly OR monthly)),
  CONSTRAINT fk_email_type_id FOREIGN KEY (email_type_id) REFERENCES email_type(email_type_id) ON DELETE CASCADE,
  CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT user_email_notification_pkey PRIMARY KEY (user_email_notification_id),
  CONSTRAINT user_email_notification_unique UNIQUE (user_id, email_type_id)
);

CREATE TABLE public.user_organization (
  organization_id integer NOT NULL,
  role_id integer NOT NULL,
  user_id integer NOT NULL,
  user_organization_id integer DEFAULT nextval('user_organization_user_organization_id_seq'::regclass) NOT NULL,
  CONSTRAINT fk_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(organization_id),
  CONSTRAINT fk_role_id FOREIGN KEY (role_id) REFERENCES roles(role_id),
  CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
  CONSTRAINT user_organization_pkey PRIMARY KEY (user_organization_id)
);

CREATE TABLE public.users (
  created_timestamp bigint NOT NULL,
  email character varying(128) NOT NULL,
  first_name character varying(128),
  keycloak_id uuid DEFAULT uuid_generate_v4() NOT NULL,
  last_name character varying(128),
  super_user bit(1) DEFAULT (0)::bit(1) NOT NULL,
  user_id integer DEFAULT nextval('users_user_id_seq'::regclass) NOT NULL,
  CONSTRAINT users_email UNIQUE (email),
  CONSTRAINT users_pkey PRIMARY KEY (user_id)
);

