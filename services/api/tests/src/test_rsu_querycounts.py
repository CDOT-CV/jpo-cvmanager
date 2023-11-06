from unittest.mock import patch, MagicMock
import pytest
import os
import api.src.rsu_querycounts as rsu_querycounts
from api.src.rsu_querycounts import query_rsu_counts_mongo
import api.tests.data.rsu_querycounts_data as querycounts_data
import datetime

##################################### Testing Requests ###########################################


def test_options_request():
    counts = rsu_querycounts.RsuQueryCounts()
    (body, code, headers) = counts.options()
    assert body == ""
    assert code == 204
    assert headers["Access-Control-Allow-Methods"] == "GET"


@patch.dict(os.environ, {"COUNTS_DB_TYPE": "BIGQUERY"})
@patch("api.src.rsu_querycounts.get_organization_rsus")
@patch("api.src.rsu_querycounts.query_rsu_counts_bq")
def test_get_request_bq(mock_query, mock_rsus):
    req = MagicMock()
    req.args = querycounts_data.request_args_good
    req.environ = querycounts_data.request_params_good
    counts = rsu_querycounts.RsuQueryCounts()
    mock_rsus.return_value = ["10.0.0.1", "10.0.0.2", "10.0.0.3"]
    mock_query.return_value = {"Some Data"}, 200
    with patch("api.src.rsu_querycounts.request", req):
        (data, code, headers) = counts.get()
        assert code == 200
        assert headers["Access-Control-Allow-Origin"] == "test.com"
        assert headers["Content-Type"] == "application/json"
        assert data == {"Some Data"}


@patch.dict(os.environ, {"COUNTS_DB_TYPE": "MONGODB"})
@patch("api.src.rsu_querycounts.get_organization_rsus")
@patch("api.src.rsu_querycounts.query_rsu_counts_mongo")
def test_get_request_mongo(mock_query, mock_rsus):
    req = MagicMock()
    req.args = querycounts_data.request_args_good
    req.environ = querycounts_data.request_params_good
    counts = rsu_querycounts.RsuQueryCounts()
    mock_rsus.return_value = ["10.0.0.1", "10.0.0.2", "10.0.0.3"]
    mock_query.return_value = {"Some Data"}, 200
    with patch("api.src.rsu_querycounts.request", req):
        (data, code, headers) = counts.get()
        assert code == 200
        assert headers["Access-Control-Allow-Origin"] == "test.com"
        assert headers["Content-Type"] == "application/json"
        assert data == {"Some Data"}


################################### Testing Data Validation #########################################

@patch.dict(os.environ, {"COUNTS_MSG_TYPES": '["test","anothErtest"]'})
def test_get_request_invalid_message():
    req = MagicMock()
    req.args = querycounts_data.request_args_bad_message
    counts = rsu_querycounts.RsuQueryCounts()
    with patch("api.src.rsu_querycounts.request", req):
        (data, code, headers) = counts.get()
        assert code == 400
        assert headers["Access-Control-Allow-Origin"] == "test.com"
        assert data == "Invalid Message Type.\nValid message types: TEST, ANOTHERTEST"

def test_get_request_invalid_message_no_env():
    req = MagicMock()
    req.args = querycounts_data.request_args_bad_message
    counts = rsu_querycounts.RsuQueryCounts()
    with patch("api.src.rsu_querycounts.request", req):
        (data, code, headers) = counts.get()
        assert code == 400
        assert headers["Access-Control-Allow-Origin"] == "test.com"
        assert data == "Invalid Message Type.\nValid message types: TIM, BSM, SPAT, PSM, MAP"

def test_schema_validate_bad_data():
    req = MagicMock()
    req.args = querycounts_data.request_args_bad_type
    counts = rsu_querycounts.RsuQueryCounts()
    with patch("api.src.rsu_querycounts.request", req):
        with pytest.raises(Exception):
            assert counts.get()


################################### Test get_organization_rsus ########################################


@patch("api.src.rsu_querycounts.pgquery")
def test_rsu_counts_get_organization_rsus(mock_pgquery):
    mock_pgquery.query_db.return_value = [({"ip": "10.11.81.12"},), ({"ip": "10.11.81.13"},), ({"ip": "10.11.81.14"},)]
    expected_query = (
        "SELECT jsonb_build_object('ip', rd.ipv4_address) "
        "FROM public.rsus AS rd "
        "JOIN public.rsu_organization_name AS ron_v ON ron_v.rsu_id = rd.rsu_id "
        f"WHERE ron_v.name = 'Test' "
        "ORDER BY rd.ipv4_address"
    )
    actual_result = rsu_querycounts.get_organization_rsus("Test")
    mock_pgquery.query_db.assert_called_with(expected_query)

    assert actual_result == ["10.11.81.12", "10.11.81.13", "10.11.81.14"]


@patch("api.src.rsu_querycounts.pgquery")
def test_rsu_counts_get_organization_rsus_empty(mock_pgquery):
    mock_pgquery.query_db.return_value = []
    expected_query = (
        "SELECT jsonb_build_object('ip', rd.ipv4_address) "
        "FROM public.rsus AS rd "
        "JOIN public.rsu_organization_name AS ron_v ON ron_v.rsu_id = rd.rsu_id "
        f"WHERE ron_v.name = 'Test' "
        "ORDER BY rd.ipv4_address"
    )
    actual_result = rsu_querycounts.get_organization_rsus("Test")
    mock_pgquery.query_db.assert_called_with(expected_query)

    assert actual_result == []


##################################### Test query_rsu_counts ###########################################
@patch.dict(os.environ, {"MONGO_DB_URI": "uri", "MONGO_DB_NAME": "name", "COUNTS_DB_NAME": "col"})
@patch("api.src.rsu_querycounts.MongoClient")
@patch("api.src.rsu_querycounts.logging")
def test_query_rsu_counts_mongo_success(mock_logging, mock_mongo):
    mock_db = MagicMock()
    mock_collection = MagicMock()
    mock_mongo.return_value.__getitem__.return_value = mock_db
    mock_db.__getitem__.return_value = mock_collection
    mock_db.validate_collection.return_value = "valid"

    # Mock data that would be returned from MongoDB
    mock_collection.find.return_value = [
        {"ip": "192.168.0.1", "road": "A1", "count": 5},
        {"ip": "192.168.0.2", "road": "A2", "count": 10},
    ]

    allowed_ips = ["192.168.0.1", "192.168.0.2"]
    message_type = "TYPE_A"
    start = "2022-01-01T00:00:00"
    end = "2023-01-01T00:00:00"

    expected_result = {"192.168.0.1": {"road": "A1", "count": 5}, "192.168.0.2": {"road": "A2", "count": 10}}

    result, status_code = query_rsu_counts_mongo(allowed_ips, message_type, start, end)
    assert result == expected_result
    assert status_code == 200


@patch.dict(os.environ, {"MONGO_DB_URI": "uri", "MONGO_DB_NAME": "name", "COUNTS_DB_NAME": "col"})
@patch("api.src.rsu_querycounts.MongoClient")
@patch("api.src.rsu_querycounts.logging")
def test_query_rsu_counts_mongo_failure(mock_logging, mock_mongo):
    # Mock the MongoDB connection to throw an exception
    mock_mongo.side_effect = Exception("Failed to connect")

    allowed_ips = ["192.168.0.1", "192.168.0.2"]
    message_type = "TYPE_A"
    start = "2022-01-01T00:00:00"
    end = "2023-01-01T00:00:00"

    result, status_code = query_rsu_counts_mongo(allowed_ips, message_type, start, end)
    assert result == {}
    assert status_code == 503

@patch('api.src.rsu_querycounts.bigquery')
def test_rsu_counts_multiple_result(mock_bigquery):
    mock_bigquery.Client.return_value.query.return_value = [querycounts_data.rsu_one, 
                                                            querycounts_data.rsu_two, 
                                                            querycounts_data.rsu_three]
    expected_rsu_data = querycounts_data.rsu_counts_expected_multiple
    with patch.dict('api.src.rsu_querycounts.os.environ', {'COUNTS_DB_NAME': 'Fake_table', "COUNTS_DB_TYPE": "BIGQUERY"}):
        (data, code) = rsu_querycounts.query_rsu_counts_bq(['10.11.81.24', '172.16.28.23', '172.16.28.136'], 'BSM', '2022-05-23T12:00:00', '2022-05-24T12:00:00')
        assert data == expected_rsu_data
        assert code == 200


@patch('api.src.rsu_querycounts.bigquery')
def test_rsu_counts_limited_rsus(mock_bigquery):
    mock_bigquery.Client.return_value.query.return_value = [querycounts_data.rsu_one, 
                                                            querycounts_data.rsu_two, 
                                                            querycounts_data.rsu_three]
    expected_rsu_data = querycounts_data.rsu_counts_expected_limited_rsus
    with patch.dict('api.src.rsu_querycounts.os.environ', {'COUNTS_DB_NAME': 'Fake_table', "COUNTS_DB_TYPE": "BIGQUERY"}):
        (data, code) = rsu_querycounts.query_rsu_counts_bq(['172.16.28.23', '172.16.28.136'], 'BSM', '2022-05-23T12:00:00', '2022-05-24T12:00:00')
        assert data == expected_rsu_data
        assert code == 200
