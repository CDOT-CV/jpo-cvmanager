BEGIN;

-- Supports the admin request history, which lists the newest requests first.
CREATE INDEX idx_obu_ota_requests_request_datetime
    ON public.obu_ota_requests (request_datetime DESC);

-- Supports serial number filtering and the OTA server's per-OBU retention cleanup,
-- which keeps only the newest successful requests for each serial number.
CREATE INDEX idx_obu_ota_requests_sn_status_datetime
    ON public.obu_ota_requests (obu_sn, error_status, request_datetime);

COMMENT ON COLUMN public.obu_ota_requests.request_datetime IS
    'UTC time the OBU OTA server received the firmware download request.';

COMMIT;
