from flask import request, abort
from flask_restful import Resource
from marshmallow import Schema, fields
import common.snmp.rsu_message_forward_helpers as rsu_message_forward_helpers
import common.util as util
import api_environment
import logging
from rsu_commands import fetch_rsu_info
from common.snmp.update_pg.update_rsu_message_forward import UpdatePostgresRsuMessageForward

from common.auth_tools import (
    ORG_ROLE_LITERAL,
    PermissionResult,
    require_permission,
)


def format_direct_snmp_fwd_configs(config_list):
    msgfwd_configs_dict = {}
    for row in config_list:
        config_row = {
            "Message Type": row["message_type"].upper(),
            "IP": row["dest_ipv4"],
            "Port": row["dest_port"],
            "Start DateTime": util.format_date_denver_iso(row["start_datetime"]),
            "End DateTime": util.format_date_denver_iso(row["end_datetime"]),
            "Config Active": rsu_message_forward_helpers.active(row["active"]),
            "Full WSMP": rsu_message_forward_helpers.active(row["security"]),
        }

        # Based on the value of msgfwd_type, store the configuration data to match the response object of rsufwdsnmpwalk
        if row["msgfwd_type"] == "rsuDsrcFwd":
            msgfwd_configs_dict[row["snmp_index"]] = config_row
        elif row["msgfwd_type"] == "rsuReceivedMsg":
            if "rsuReceivedMsgTable" not in msgfwd_configs_dict:
                msgfwd_configs_dict["rsuReceivedMsgTable"] = {}
            msgfwd_configs_dict["rsuReceivedMsgTable"][row["snmp_index"]] = config_row
        elif row["msgfwd_type"] == "rsuXmitMsgFwding":
            if "rsuXmitMsgFwdingTable" not in msgfwd_configs_dict:
                msgfwd_configs_dict["rsuXmitMsgFwdingTable"] = {}
            msgfwd_configs_dict["rsuXmitMsgFwdingTable"][row["snmp_index"]] = config_row
        else:
            logging.warning(
                f"Encountered unknown message forwarding configuration type '{row['msgfwd_type']}'"
            )

    # Make sure both RX and TX objects are available if the RSU ends up having NTCIP 1218 configurations
    if (
        "rsuReceivedMsgTable" in msgfwd_configs_dict
        and "rsuXmitMsgFwdingTable" not in msgfwd_configs_dict
    ):
        msgfwd_configs_dict["rsuXmitMsgFwdingTable"] = {}
    elif (
        "rsuXmitMsgFwdingTable" in msgfwd_configs_dict
        and "rsuReceivedMsgTable" not in msgfwd_configs_dict
    ):
        msgfwd_configs_dict["rsuReceivedMsgTable"] = {}

    return {"RsuFwdSnmpwalk": msgfwd_configs_dict}


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

            return format_direct_snmp_fwd_configs(rsu_configs), 200, self.headers
        except Exception as e:
            logging.error(f"Error fetching SNMP configs: {e}")
            return {"message": f"Error fetching SNMP configs: {e}"}, 500
