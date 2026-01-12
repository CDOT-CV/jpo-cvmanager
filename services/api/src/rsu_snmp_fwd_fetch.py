from flask import request, abort
from flask_restful import Resource
from marshmallow import Schema, fields
import common.snmp.rsu_message_forward_helpers as rsu_message_forward_helpers
import api_environment
import logging
from rsu_commands import fetch_rsu_info
from common.snmp.update_pg.update_rsu_message_forward import UpdatePostgresRsuMessageForward

from common.auth_tools import (
    ORG_ROLE_LITERAL,
    PermissionResult,
    require_permission,
)


# REST endpoint resource class and schema
class RsuSnmpFwdFetchSchema(Schema):
    rsu_ip = fields.IPv4(required=True)


class RsuSnmpFwdFetch(Resource):
    options_headers = {
        "Access-Control-Allow-Origin": api_environment.CORS_DOMAIN,
        "Access-Control-Allow-Headers": "Content-Type,Authorization,Organization",
        "Access-Control-Allow-Methods": "GET",
        "Access-Control-Max-Age": "3600",
    }

    headers = {
        "Access-Control-Allow-Origin": api_environment.CORS_DOMAIN,
        "Content-Type": "application/json",
    }

    def options(self):
        # CORS support
        return ("", 204, self.options_headers)

    @require_permission(required_role=ORG_ROLE_LITERAL.USER)
    def get(self, permission_result: PermissionResult):
        logging.debug("RsuSnmpFwdFetch GET requested")
        # Schema check for arguments
        schema = RsuSnmpFwdFetchSchema()
        errors = schema.validate(request.args)
        if errors:
            abort(400, str(errors))

        # Get arguments from request
        rsu_ip = request.args.get("rsu_ip")
        organization = permission_result.user.organization

        # Fetch RSU info
        rsu_info = fetch_rsu_info(rsu_ip, organization)
        if not rsu_info:
            abort(
                404, f"RSU with IP {rsu_ip} not found or not authorized for your organization"
            )

        # Call get_snmp_configs
        updater = UpdatePostgresRsuMessageForward()
        # get_snmp_configs expects a list of RSU objects with specific keys
        # fetch_rsu_info returns rsu_id, manufacturer, ssh_username, ssh_password, snmp_username, snmp_password, snmp_encrypt_pw, snmp_version
        # UpdatePostgresRsuMessageForward.get_snmp_configs uses: ipv4_address, snmp_username, snmp_password, snmp_encrypt_pw, snmp_version, rsu_id
        rsu_info["ipv4_address"] = rsu_ip
        
        try:
            configs = updater.get_snmp_configs([rsu_info])
            rsu_configs = configs.get(rsu_info["rsu_id"])

            if rsu_configs == "Unable to retrieve latest SNMP config":
                return {"message": "Unable to retrieve latest SNMP config from RSU"}, 500
            if rsu_configs == "Unsupported SNMP version":
                return {"message": "Unsupported SNMP version for direct fetch"}, 400

            return (
                rsu_message_forward_helpers.format_snmp_msgfwd_configs(
                    rsu_configs, rsu_ip=rsu_ip
                ),
                200,
                self.headers,
            )
        except Exception as e:
            logging.error(f"Error fetching SNMP configs: {e}")
            return {"message": f"Error fetching SNMP configs: {e}"}, 500
