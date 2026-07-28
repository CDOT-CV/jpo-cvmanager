from unittest.mock import patch
import api.src.rsu_commands as rsu_commands

# shared arguments
rsu_ip = ["192.168.0.20"]
args = "test"
rsu_info = {
    "rsu_ip": rsu_ip,
    "manufacturer": "test",
    "snmp_username": "test",
    "snmp_password": "test",
    "snmp_encrypt_pw": None,
    "snmp_version": "test",
    "ssh_username": "test",
    "ssh_password": "test",
}
organization = "test"


# ## RSU_COMMANDS TESTS ###
def test_rsu_commands_snmpfilter_option_present():
    expected_value = {
        "roles": ["operator", "admin"],
        "ssh_required": True,
        "snmp_required": False,
    }

    assert rsu_commands.command_data["snmpfilter"]["roles"] == expected_value["roles"]
    assert (
        rsu_commands.command_data["snmpfilter"]["ssh_required"]
        == expected_value["ssh_required"]
    )
    assert (
        rsu_commands.command_data["snmpfilter"]["snmp_required"]
        == expected_value["snmp_required"]
    )


@patch("api.src.rsu_commands.ssh_commands.reboot")
def test_execute_command_reboot(mock_ssh_commands_reboot):
    # mock
    mock_ssh_commands_reboot.return_value = "mocked ssh_commands.reboot"
    rsu_commands.command_data["reboot"]["function"] = mock_ssh_commands_reboot

    # call
    command = "reboot"
    result = rsu_commands.execute_command(command, rsu_ip, args, rsu_info)

    # check
    mock_ssh_commands_reboot.assert_called_once()
    expected_result = "mocked ssh_commands.reboot"
    assert result == expected_result


@patch("api.src.rsu_commands.ssh_commands.snmpfilter")
def test_execute_command_snmpfilter(mock_ssh_commands_snmpfilter):
    # mock
    mock_ssh_commands_snmpfilter.return_value = "mocked ssh_commands.snmpfilter"
    rsu_commands.command_data["snmpfilter"]["function"] = mock_ssh_commands_snmpfilter

    # call
    command = "snmpfilter"
    result = rsu_commands.execute_command(command, rsu_ip, args, rsu_info)

    # check
    mock_ssh_commands_snmpfilter.assert_called_once()
    expected_result = "mocked ssh_commands.snmpfilter"
    assert result == expected_result


# test queries for RSU manufacturer, SSH credentials, and SNMP credentials
@patch("api.src.rsu_commands.pgquery.query_db")
def test_fetch_rsu_info(mock_query_db):
    # mock
    mock_query_db.return_value = [
        (
            {
                "rsu_id": 24,
                "manufacturer_name": "mocked manufacturer_name",
                "ssh_username": "mocked ssh_username",
                "ssh_password": "mocked ssh_password",
                "snmp_username": "mocked snmp_username",
                "snmp_password": "mocked snmp_password",
                "snmp_encrypt_pw": "mocked snmp_encrypt_pw",
                "snmp_version": "mocked snmp_version",
            },
        ),
    ]

    # call
    result = rsu_commands.fetch_rsu_info(rsu_ip, organization)

    # check
    mock_query_db.assert_called_once()
    expected_result = {
        "rsu_id": 24,
        "manufacturer": "mocked manufacturer_name",
        "ssh_username": "mocked ssh_username",
        "ssh_password": "mocked ssh_password",
        "snmp_username": "mocked snmp_username",
        "snmp_password": "mocked snmp_password",
        "snmp_encrypt_pw": "mocked snmp_encrypt_pw",
        "snmp_version": "mocked snmp_version",
    }
    assert result == expected_result


@patch("api.src.rsu_commands.execute_command")
def test_perform_command_unknown_command(mock_execute_command):
    # call
    command = "unknown-command"
    role = "rsu"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check
    expected_result = ("Command unknown: unknown-command", 400)
    assert result == expected_result
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.fetch_rsu_info")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_incomplete_rsu_data(mock_execute_command, mock_fetch_rsu_info, mock_get_rsu_owner_org):
    # mock
    mock_get_rsu_owner_org.return_value = organization
    mock_fetch_rsu_info.return_value = None

    # call
    command = "reboot"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check
    expected_result = (
        "Provided RSU IP does not have complete RSU data for organization: test::192.168.0.20",
        500,
    )
    assert result == expected_result
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.fetch_rsu_info")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_unauthorized_role(mock_execute_command, mock_fetch_rsu_info):
    # mock
    mock_fetch_rsu_info.return_value = "mocked fetch_rsu_info"

    # call
    command = "reboot"
    role = "rsu"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check
    expected_result = ("Unauthorized role to run reboot", 401)
    assert result == expected_result
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.pgquery.query_db")
def test_get_rsu_owner_org(mock_query_db):
    # mock
    mock_query_db.return_value = [("test_org",)]

    # call
    result = rsu_commands.get_rsu_owner_org("192.168.0.20")

    # check
    mock_query_db.assert_called_once()
    assert result == "test_org"


@patch("api.src.rsu_commands.pgquery.query_db")
def test_get_rsu_owner_org_not_found(mock_query_db):
    # mock
    mock_query_db.return_value = []

    # call
    result = rsu_commands.get_rsu_owner_org("192.168.0.20")

    # check
    assert result is None


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_non_owner_org(mock_execute_command, mock_get_rsu_owner_org):
    # mock
    mock_get_rsu_owner_org.return_value = "other_org"

    # call
    command = "reboot"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check
    expected_result = (
        f"Organization '{organization}' does not own the following RSUs: {rsu_ip[0]} (owner: 'other_org')",
        403,
    )
    assert result == expected_result
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_multiple_non_owner_rsus(mock_execute_command, mock_get_rsu_owner_org):
    # Three RSUs: first is owned, second and third are not
    multi_rsu_ip = ["192.168.0.20", "192.168.0.21", "192.168.0.22"]
    mock_get_rsu_owner_org.side_effect = [organization, "other_org_1", "other_org_2"]

    # call
    command = "reboot"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, multi_rsu_ip, args)

    # check
    assert result[1] == 403
    assert "192.168.0.21" in result[0]
    assert "other_org_1" in result[0]
    assert "192.168.0.22" in result[0]
    assert "other_org_2" in result[0]
    assert "192.168.0.20" not in result[0]
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.fetch_rsu_info")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_ignores_rsu_without_owner_org(mock_execute_command, mock_fetch_rsu_info, mock_get_rsu_owner_org):
    # mock: RSU has no owner org found
    mock_get_rsu_owner_org.return_value = None
    mock_fetch_rsu_info.return_value = rsu_info

    # call
    command = "reboot"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check — command proceeds instead of being aborted
    mock_execute_command.assert_called_once()
    assert result != (f"RSU {rsu_ip[0]} not found or has no owner organization", 404)


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_multiple_rsus_ignores_ownerless_but_blocks_non_owner(mock_execute_command, mock_get_rsu_owner_org):
    # Three RSUs: first is owned, second has no owner org at all, third is owned by a different org
    multi_rsu_ip = ["192.168.0.20", "192.168.0.21", "192.168.0.22"]
    mock_get_rsu_owner_org.side_effect = [organization, None, "other_org"]

    # call
    command = "reboot"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, multi_rsu_ip, args)

    # check — ownerless RSU is ignored (not reported), non-owner RSU still blocks the request
    assert result[1] == 403
    assert "192.168.0.21" not in result[0]
    assert "192.168.0.22" in result[0]
    assert "other_org" in result[0]
    mock_execute_command.assert_not_called()


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.fetch_rsu_info")
@patch("api.src.rsu_commands.execute_command")
def test_perform_command_super_user_bypasses_owner_check(mock_execute_command, mock_fetch_rsu_info, mock_get_rsu_owner_org):
    # mock
    mock_fetch_rsu_info.return_value = rsu_info

    # call
    command = "reboot"
    role = "admin"
    rsu_commands.perform_command(command, organization, role, rsu_ip, args, super_user=True)

    # check
    mock_get_rsu_owner_org.assert_not_called()
    mock_execute_command.assert_called_once()


@patch("api.src.rsu_commands.get_rsu_owner_org")
def test_perform_command_upgrade_check_requires_owner_org(mock_get_rsu_owner_org):
    # mock
    mock_get_rsu_owner_org.return_value = "other_org"

    # call
    command = "upgrade-check"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check — non-owner org is blocked with 403
    assert result[1] == 403
    mock_get_rsu_owner_org.assert_called_once()


@patch("api.src.rsu_commands.get_rsu_owner_org")
@patch("api.src.rsu_commands.execute_upgrade_rsu")
def test_perform_command_upgrade_rsu_requires_owner_org(mock_execute_upgrade_rsu, mock_get_rsu_owner_org):
    # mock
    mock_get_rsu_owner_org.return_value = "other_org"

    # call
    command = "upgrade-rsu"
    role = "admin"
    result = rsu_commands.perform_command(command, organization, role, rsu_ip, args)

    # check — non-owner org is blocked with 403
    assert result[1] == 403
    mock_get_rsu_owner_org.assert_called_once()
    mock_execute_upgrade_rsu.assert_not_called()


# TODO: test RsuCommandRequest class
