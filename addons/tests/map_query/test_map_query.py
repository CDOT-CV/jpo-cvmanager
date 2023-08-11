from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timedelta
import json
import os
import copy
import hashlib
from images.map_query import map_query


@patch("images.map_query.map_query.json.loads")
def test_process_message(mock_json_loads):
    emptymap = {}
    map_query.hashmap = emptymap
    mock_json_msg = {
        "payload": {
            "data": {
                "intersections": {
                    "intersectionGeometry": [
                        # Your data here
                    ]
                }
            }
        },
        "metadata": {
            "originIp": "127.0.0.1",
            "odeReceivedAt": "2023-08-10T12:00:00Z",
            # Add other metadata
        },
    }

    mock_msg = Mock(
        value=Mock(decode=lambda encoding: json.dumps(mock_json_msg).encode("utf-8"))
    )
    map_query.process_message(mock_msg)


@patch("images.map_query.map_query.json.loads")
def test_process_message_exists(mock_json_loads):
    emptymap = {}
    map_query.hashmap = emptymap
    mock_json_msg = {
        "payload": {
            "data": {
                "intersections": {
                    "intersectionGeometry": [
                        # Your data here
                    ]
                }
            }
        },
        "metadata": {
            "originIp": "127.0.0.1",
            "odeReceivedAt": "2000-08-10T12:00:00Z",
            "serialId": "stuff",
        },
    }
    copy_json = copy.deepcopy(mock_json_msg)
    del copy_json["metadata"]["odeReceivedAt"]
    del copy_json["metadata"]["serialId"]
    hash_string = hashlib.sha256(str(copy_json).encode("utf-8")).hexdigest()
    map_query.hashmap[hash_string] = datetime.now() - timedelta(minutes=61)

    msg = MagicMock()
    msg.value.decode.return_value = json.dumps(mock_json_msg)
    mock_json_loads.return_value = mock_json_msg
    map_query.process_message(msg)


@patch("images.map_query.map_query.db")
@patch("images.map_query.cvmsg_functions.map_intersection_geometry_to_geojson")
def test_insert_map_msg(mock_map_intersection_geojson, mock_db):
    mock_map_msg = {
        "payload": {"data": {"intersections": {"intersectionGeometry": [{"data": 0}]}}},
        "metadata": {
            "originIp": "127.0.0.1",
            "odeReceivedAt": "2023-08-10T12:00:00Z",
            # Add other metadata
        },
    }

    mock_db.connect.return_value.__enter__.return_value.execute.return_value = None
    mock_map_intersection_geojson.return_value = {}

    result = map_query.insert_map_msg(mock_map_msg)

    assert result == "update successful"


def test_cleanup_hashmap():
    emptymap = {}
    map_query.hashmap = emptymap
    map_query.hashmap["test"] = datetime.now()
    map_query.hashmap["test_old"] = datetime.now() - timedelta(hours=2)
    map_query.cleanup_hashmap()
    result = map_query.hashmap

    assert len(map_query.hashmap) == 1


@patch.dict(
    os.environ,
    {"INPUT_TOPIC": "test"},
)
@patch("images.map_query.map_query.process_message")
@patch("images.map_query.map_query.kafka_helper.create_consumer")
def test_start_consumers(mock_create_consumer, mock_process_message):
    mock_create_consumer.return_value = "m"
    mock_process_message.return_value = "update successful"
    map_query.start_consumers(False)

    mock_create_consumer.assert_called_once_with("test")
    mock_process_message.assert_called_once_with("m")
