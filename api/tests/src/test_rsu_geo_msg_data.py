from unittest.mock import patch, MagicMock
import os
import src.rsu_geo_msg_data as rsu_geo_msg_data
from src.rsu_geo_msg_data import query_geo_data_mongo, msg_hash, query_bsm_data_bq
import tests.data.rsu_geo_msg_data as data


###################################### Testing Requests ##########################################

# OPTIONS endpoint test

def test_request_options():
  info = rsu_geo_msg_data.RsuGeoMsgData()
  (body, code, headers) = info.options()
  assert body == ''
  assert code == 204
  assert headers['Access-Control-Allow-Methods'] == 'POST'

# POST endpoint tests
@patch.dict(os.environ, {"COUNTS_DB_TYPE": "BIGQUERY"})
@patch('src.rsu_geo_msg_data.query_bsm_data_bq')
def test_entry_post_bq_bsm(mock_query_bsm_data_bq):
  req = MagicMock()
  req.json = data.request_params_bsm
  mock_query_bsm_data_bq.return_value = [], 200
  with patch("src.rsu_geo_msg_data.request", req):
      status = rsu_geo_msg_data.RsuGeoMsgData()
      (body, code, headers) = status.post()
      mock_query_bsm_data_bq.assert_called_once_with(data.geometry, "start_date", "end_date") 
      assert code == 200
      assert headers['Access-Control-Allow-Origin'] == "*"
      assert body == []
      
@patch.dict(os.environ, {"COUNTS_DB_TYPE": "MONGODB"})
@patch('src.rsu_geo_msg_data.query_geo_data_mongo')
def test_entry_post_mongo_bsm(mock_query_geo_data_mongo):
  req = MagicMock()
  req.json = data.request_params_bsm
  mock_query_geo_data_mongo.return_value = [], 200
  with patch("src.rsu_geo_msg_data.request", req):
      status = rsu_geo_msg_data.RsuGeoMsgData()
      (body, code, headers) = status.post()
      mock_query_geo_data_mongo.assert_called_once_with(data.geometry, "start_date", "end_date", "Bsm") 
      assert code == 200
      assert headers['Access-Control-Allow-Origin'] == "*"
      assert body == []      

@patch.dict(os.environ, {"COUNTS_DB_TYPE": "MONGODB"})
@patch('src.rsu_geo_msg_data.query_geo_data_mongo')
def test_entry_post_mongo_psm(mock_query_geo_data_mongo):
  req = MagicMock()
  req.json = data.request_params_psm
  mock_query_geo_data_mongo.return_value = [], 200
  with patch("src.rsu_geo_msg_data.request", req):
      status = rsu_geo_msg_data.RsuGeoMsgData()
      (body, code, headers) = status.post()
      mock_query_geo_data_mongo.assert_called_once_with(data.geometry, "start_date", "end_date", "Psm") 
      assert code == 200
      assert headers['Access-Control-Allow-Origin'] == "*"
      assert body == []   
      
@patch.dict(os.environ, {"COUNTS_DB_TYPE": "BIGQUERY"})
def test_entry_post_bq_unsupported_msg():
  req = MagicMock()
  req.json = data.request_params_psm
  with patch("src.rsu_geo_msg_data.request", req):
      status = rsu_geo_msg_data.RsuGeoMsgData()
      (body, code, headers) = status.post()
      print (body, code, headers)
      assert code == 400
      assert headers['Access-Control-Allow-Origin'] == "*"
      assert body == []   

###################################### Testing Functions ##########################################

def test_msg_hash():
    result = msg_hash("192.168.1.1", "Bsm", 1616636734, 123.4567, 234.5678)
    assert result is not None


@patch.dict(os.environ, {"MONGO_DB_URI": "uri", "MONGO_DB_NAME": "name", "BSM_DB_NAME": "col"})
@patch("src.rsu_geo_msg_data.MongoClient")
def test_query_geo_data_mongo(mock_mongo):
    mock_db = MagicMock()
    mock_collection = MagicMock()
    mock_mongo.return_value.__getitem__.return_value = mock_db
    mock_db.__getitem__.return_value = mock_collection
    mock_db.validate_collection.return_value = "valid"

    mock_collection.find.return_value = data.mongo_bsm_data_response

    start = "2023-07-01T00:00:00Z"
    end = "2023-07-02T00:00:00Z"
    response, code = query_geo_data_mongo(data.point_list, start, end, "Bsm")
    expected_response = data.processed_bsm_message_data

    mock_mongo.assert_called()
    mock_collection.find.assert_called()
    assert code == 200
    assert response == expected_response


@patch.dict(os.environ, {"MONGO_DB_URI": "uri", "MONGO_DB_NAME": "name", "BSM_DB_NAME": "col"})
@patch("src.rsu_geo_msg_data.MongoClient")
def test_query_geo_data_mongo_filter_failed(mock_mongo):
    mock_db = MagicMock()
    mock_collection = MagicMock()
    mock_mongo.return_value.__getitem__.return_value = mock_db
    mock_db.__getitem__.return_value = mock_collection
    mock_db.validate_collection.return_value = "valid"

    mock_collection.find.side_effect = Exception("Failed to find")

    start = "2023-07-01T00:00:00Z"
    end = "2023-07-02T00:00:00Z"
    response, code = query_geo_data_mongo(data.point_list, start, end, "Bsm")
    expected_response = []

    mock_mongo.assert_called()
    mock_collection.find.assert_called()
    assert code == 500
    assert response == expected_response


@patch.dict(os.environ, {"MONGO_DB_URI": "uri", "MONGO_DB_NAME": "name", "BSM_DB_NAME": "col"})
@patch("src.rsu_geo_msg_data.MongoClient")
def test_query_geo_data_mongo_failed_to_connect(mock_mongo):
    mock_mongo.side_effect = Exception("Failed to connect")

    start = "2023-07-01T00:00:00Z"
    end = "2023-07-02T00:00:00Z"
    response, code = query_geo_data_mongo(data.point_list, start, end, "Bsm")
    expected_response = []

    mock_mongo.assert_called()
    assert code == 503
    assert response == expected_response


@patch.dict(os.environ, {"BSM_DB_NAME": "col"})
@patch("src.rsu_geo_msg_data.bigquery")
def test_query_bsm_data_bq(mock_bq):
    mock_bq_client = MagicMock()
    mock_bq.Client.return_value = mock_bq_client

    mock_job = MagicMock()
    mock_bq_client.query.return_value = mock_job
    mock_job.__iter__.return_value = data.bq_bsm_data_response

    point_list = [[1, 2], [3, 4]]
    start = "2023-07-01T00:00:00Z"
    end = "2023-07-02T00:00:00Z"

    response, code = query_bsm_data_bq(point_list, start, end)
    expected_response = data.processed_bsm_message_data

    assert response[0]["properties"]["id"] == expected_response[0]["properties"]["id"]
    assert response[0]["properties"]["time"] == expected_response[0]["properties"]["time"]
    assert code == 200  # Expect a success status code
