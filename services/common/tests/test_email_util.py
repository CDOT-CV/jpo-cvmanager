from mock import patch
from common import email_util


@patch("common.email_util.query_db")
def test_get_email_list_no_org(mock_query_db):
    mock_query_db.return_value = [
        [
            {"email": "bob@gmail.com"},
        ],
        [{"email": "bob2@gmail.com"}],
    ]
    email_list = email_util.get_email_list("msg_type")
    assert "bob@gmail.com" in email_list
    assert "bob2@gmail.com" in email_list
    assert len(email_list) == 2


@patch("common.email_util.query_db")
@patch("common.email_util.build_org_email_list")
def test_get_email_list_org(mock_build_org_email_list, mock_query_db):
    mock_query_db.return_value = [
        [
            {"email": "bob@gmail.com"},
        ],
        [{"email": "bob2@gmail.com"}],
    ]
    mock_build_org_email_list.return_value = ["org@gmail.com"]
    email_list = email_util.get_email_list("msg_type", "name")
    assert "org@gmail.com" in email_list
    assert len(email_list) == 1


@patch("common.email_util.query_db")
@patch("common.email_util.build_user_email_list")
def test_get_email_list_from_rsu(mock_build_user_email_list, mock_query_db):
    mock_query_db.return_value = [
        [{"name": "Test Org", "email": "bob@gmail.com"}],
        [{"name": "Test Org2", "email": None}],
        [{"name": "Test Org3", "email": ""}],
    ]
    mock_build_user_email_list.return_value = ["org2@gmail.com", "org3@gmail.com"]
    email_list = email_util.get_email_list_from_rsu("msg_type", "127.0.0.1")
    assert "org3@gmail.com" in email_list
    assert "org2@gmail.com" in email_list
    assert "bob@gmail.com" in email_list
    assert len(email_list) == 3


@patch("common.email_util.query_db")
def test_build_user_email_list_scopes_to_subscription_organization(mock_query_db):
    mock_query_db.return_value = [[{"email": "bob@gmail.com"}]]

    email_list = email_util.build_user_email_list("Daily Message Counts", "Org A")

    assert email_list == ["bob@gmail.com"]
    query, params = mock_query_db.call_args.args
    assert "uen.organization_id" in query
    assert "user_organization" not in query
    assert params == {"msg_type": "Daily Message Counts", "org_name": "Org A"}


@patch("common.email_util.query_db")
def test_build_user_email_list_without_organization(mock_query_db):
    mock_query_db.return_value = []

    email_util.build_user_email_list("Critical Error Messages", None)

    query, params = mock_query_db.call_args.args
    assert "organization_id" not in query
    assert params == {"msg_type": "Critical Error Messages"}
