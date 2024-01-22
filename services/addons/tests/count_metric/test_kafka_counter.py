import os
import pytest
from mock import call, MagicMock, patch
from confluent_kafka import KafkaError, KafkaException
from addons.images.count_metric import kafka_counter


def createKafkaMessageCounter(type: int):
    kafka_counter.bigquery.Client = MagicMock()
    kafka_counter.bigquery.Client.return_value = MagicMock()
    kafka_counter.bigquery.Client.return_value.query = MagicMock()
    kafka_counter.bigquery.Client.return_value.query.return_value.result = MagicMock()
    kafka_counter.bigquery.Client.return_value.query.return_value.result.return_value.total_rows = (
        1
    )
    kafka_counter.pymongo.MongoClient = MagicMock()
    kafka_counter.pymongo.MongoClient.return_value = MagicMock()
    kafka_counter.bigquery.Client.__getitem__.return_value.__getitem__.return_value = (
        MagicMock()
    )
    kafka_counter.bigquery.Client.__getitem__.return_value.__getitem__.return_value.insert_many.return_value = (
        MagicMock()
    )
    thread_id = 0
    message_type = "bsm"
    rsu_location_dict = {"noIP": "Unknown"}
    rsu_count_dict = {"Unknown": {"noIP": 1}}
    rsu_count_dict_zero = {"Unknown": {"noIP": 0}}
    newKafkaMessageCounter = kafka_counter.KafkaMessageCounter(
        thread_id,
        message_type,
        rsu_location_dict,
        rsu_count_dict,
        rsu_count_dict_zero,
        type,
    )

    return newKafkaMessageCounter


def test_write_bq_with_type0_kmc_success():
    # prepare
    os.environ["DESTINATION_DB"] = "BIGQUERY"
    os.environ["KAFKA_BIGQUERY_TABLENAME"] = "test"
    kafkaMessageCounterType0 = createKafkaMessageCounter(0)
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.info = MagicMock()

    # call
    query_values = "test"
    kafkaMessageCounterType0.write_bigquery(query_values)

    # check
    targetTable = os.getenv("KAFKA_BIGQUERY_TABLENAME")
    expectedArgument = f"INSERT INTO `{targetTable}`(RSU, Road, Date, Type, Count) VALUES {query_values}"
    kafkaMessageCounterType0.bq_client.query.assert_called_once_with(expectedArgument)
    kafkaMessageCounterType0.bq_client.query.return_value.result.assert_called_once()
    kafka_counter.logging.info.assert_called_once()


def test_write_bq_with_type1_kmc_success():
    # prepare
    os.environ["DESTINATION_DB"] = "BIGQUERY"
    os.environ["PUBSUB_BIGQUERY_TABLENAME"] = "test"
    kafkaMessageCounterType1 = createKafkaMessageCounter(1)
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.info = MagicMock()

    # call
    query_values = "test"
    kafkaMessageCounterType1.write_bigquery(query_values)

    # check
    targetTable = os.getenv("PUBSUB_BIGQUERY_TABLENAME")
    expectedArgument = f"INSERT INTO `{targetTable}`(RSU, Road, Date, Type, Count) VALUES {query_values}"
    kafkaMessageCounterType1.bq_client.query.assert_called_once_with(expectedArgument)
    kafkaMessageCounterType1.bq_client.query.return_value.result.assert_called_once()
    kafka_counter.logging.info.assert_called_once()


def test_push_metrics_bq_success():
    os.environ["DESTINATION_DB"] = "BIGQUERY"
    os.environ["PUBSUB_BIGQUERY_TABLENAME"] = "test"
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafkaMessageCounter.write_bigquery = MagicMock()

    # call
    kafkaMessageCounter.push_metrics()

    # check
    kafkaMessageCounter.write_bigquery.assert_called_once()


def test_push_metrics_bq_exception():
    os.environ["DESTINATION_DB"] = "BIGQUERY"
    os.environ["PUBSUB_BIGQUERY_TABLENAME"] = "test"
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafkaMessageCounter.write_bigquery = MagicMock()
    kafkaMessageCounter.write_bigquery.side_effect = Exception("test")
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.error = MagicMock()

    # call
    kafkaMessageCounter.push_metrics()

    # check
    kafkaMessageCounter.write_bigquery.assert_called_once()
    kafka_counter.logging.error.assert_called_once()


def test_write_mongo_with_type0_kmc_success():
    # prepare
    os.environ["DESTINATION_DB"] = "MONGODB"
    os.environ["MONGO_DB_URI"] = "URI"
    os.environ["INPUT_COUNTS_MONGO_COLLECTION_NAME"] = "test_input"
    kafkaMessageCounterType0 = createKafkaMessageCounter(0)
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.info = MagicMock()

    # call
    test_doc = {"test": "doc"}
    kafkaMessageCounterType0.write_mongo(test_doc)

    # check
    kafkaMessageCounterType0.mongo_client[os.getenv("MONGO_DB_NAME")][
        os.getenv("INPUT_COUNTS_MONGO_COLLECTION_NAME")
    ].insert_many.assert_called_once_with(test_doc)
    kafka_counter.logging.info.assert_called_once()


def test_write_mongo_with_type1_kmc_success():
    # prepare
    os.environ["DESTINATION_DB"] = "MONGODB"
    os.environ["MONGO_DB_URI"] = "URI"
    os.environ["OUTPUT_COUNTS_MONGO_COLLECTION_NAME"] = "test_output"
    kafkaMessageCounterType1 = createKafkaMessageCounter(1)
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.info = MagicMock()

    # call
    test_doc = {"test": "doc"}
    kafkaMessageCounterType1.write_mongo(test_doc)

    # check
    kafkaMessageCounterType1.mongo_client[os.getenv("MONGO_DB_NAME")][
        os.getenv("OUTPUT_COUNTS_MONGO_COLLECTION_NAME")
    ].insert_many.assert_called_once_with(test_doc)
    kafka_counter.logging.info.assert_called_once()


def test_push_metrics_mongo_success():
    os.environ["DESTINATION_DB"] = "MONGODB"
    os.environ["MONGO_DB_URI"] = "URI"
    os.environ["INPUT_COUNTS_MONGO_COLLECTION_NAME"] = "test_input"
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafkaMessageCounter.write_mongo = MagicMock()

    # call
    kafkaMessageCounter.push_metrics()

    # check
    kafkaMessageCounter.write_mongo.assert_called_once()


def test_push_metrics_mongo_exception():
    os.environ["DESTINATION_DB"] = "MONGODB"
    os.environ["MONGO_DB_URI"] = "URI"
    os.environ["INPUT_COUNTS_MONGO_COLLECTION_NAME"] = "test_input"
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafkaMessageCounter.write_mongo = MagicMock()
    kafkaMessageCounter.write_mongo.side_effect = Exception("test")
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.error = MagicMock()

    # call
    kafkaMessageCounter.push_metrics()

    # check
    kafkaMessageCounter.write_mongo.assert_called_once()
    kafka_counter.logging.error.assert_called_once()


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.json")
def test_process_message_with_type0_kmc_origin_ip_present_success(
    mock_json, mock_logging
):
    kafkaMessageCounter = createKafkaMessageCounter(0)
    originIp = "192.168.0.5"
    mock_json.loads.return_value = {
        "BsmMessageContent": [
            {
                "metadata": {
                    "utctimestamp": "2020-10-01T00:00:00.000Z",
                    "originRsu": originIp,
                },
                "payload": "00131A604A380583702005837800080008100000040583705043002580",
            }
        ]
    }
    value_return = MagicMock()
    value_return.decode.return_value = "test"
    msg = MagicMock()
    msg.value.return_value = value_return

    # call
    kafkaMessageCounter.process_message(msg)

    # check
    assert kafkaMessageCounter.rsu_count_dict["Unknown"][originIp] == 1
    mock_logging.warning.assert_not_called()
    mock_logging.error.assert_not_called()
    mock_json.loads.assert_called_once_with("test")


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.json")
def test_process_message_with_type0_kmc_malformed_message(mock_json, mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafka_counter.json.loads.return_value = {
        "BsmMessageContent": [
            {
                "metadata": {"utctimestamp": "2020-10-01T00:00:00.000Z"},
                "payload": "00131A604A380583702005837800080008100000040583705043002580",
            }
        ]
    }
    value_return = MagicMock()
    value_return.decode.return_value = "test"
    msg = MagicMock()
    msg.value.return_value = value_return

    # call
    kafkaMessageCounter.process_message(msg)

    # check
    assert kafkaMessageCounter.rsu_count_dict["Unknown"]["noIP"] == 2
    mock_logging.warning.assert_called_once()
    mock_logging.error.assert_not_called()
    mock_json.loads.assert_called_once_with("test")


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.json")
def test_process_message_with_type1_kmc_origin_ip_present_success(
    mock_json, mock_logging
):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(1)
    originIp = "192.168.0.5"
    kafka_counter.json.loads.return_value = {
        "metadata": {"utctimestamp": "2020-10-01T00:00:00.000Z", "originIp": originIp},
        "payload": "00131A604A380583702005837800080008100000040583705043002580",
    }
    value_return = MagicMock()
    value_return.decode.return_value = "test"
    msg = MagicMock()
    msg.value.return_value = value_return

    # call
    kafkaMessageCounter.process_message(msg)

    # check
    assert kafkaMessageCounter.rsu_count_dict["Unknown"][originIp] == 1
    mock_logging.warning.assert_not_called()
    mock_logging.error.assert_not_called()
    mock_json.loads.assert_called_once_with("test")


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.json")
def test_process_message_with_type1_kmc_malformed_message(mock_json, mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(1)
    kafka_counter.json.loads.return_value = {
        "metadata": {"utctimestamp": "2020-10-01T00:00:00.000Z"},
        "payload": "00131A604A380583702005837800080008100000040583705043002580",
    }
    value_return = MagicMock()
    value_return.decode.return_value = "test"
    msg = MagicMock()
    msg.value.return_value = value_return

    # call
    kafkaMessageCounter.process_message(msg)

    # check
    assert kafkaMessageCounter.rsu_count_dict["Unknown"]["noIP"] == 2
    mock_logging.warning.assert_called_once()
    mock_logging.error.assert_not_called()
    mock_json.loads.assert_called_once_with("test")


@patch("addons.images.count_metric.kafka_counter.logging")
def test_process_message_exception(mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)

    # call
    message = ""
    kafkaMessageCounter.process_message(message)

    # check
    assert kafkaMessageCounter.rsu_count_dict["Unknown"]["noIP"] == 1
    mock_logging.warning.assert_not_called()
    mock_logging.error.assert_called_once()


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.Consumer")
def test_listen_for_message_and_process_success(mock_Consumer, mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    proc_msg = MagicMock()
    proc_msg.side_effect = [True, False]
    kafkaMessageCounter.should_run = proc_msg
    kafkaMessageCounter.process_message = MagicMock()

    kafkaConsumer = MagicMock()
    mock_Consumer.return_value = kafkaConsumer
    msg = MagicMock()
    kafkaConsumer.poll.return_value = msg
    msg.error.return_value = None

    # call
    topic = "test"
    bootstrap_servers = "test"
    kafkaMessageCounter.listen_for_message_and_process(topic, bootstrap_servers)

    # check
    kafkaMessageCounter.process_message.assert_called_once()
    mock_logging.warning.assert_called_once()
    kafkaConsumer.poll.assert_called_once()
    kafkaConsumer.close.assert_called_once()


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.Consumer")
def test_listen_for_message_and_process_eof(mock_Consumer, mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    proc_msg = MagicMock()
    proc_msg.side_effect = [True, False]
    kafkaMessageCounter.should_run = proc_msg
    kafkaMessageCounter.process_message = MagicMock()

    kafkaConsumer = MagicMock()
    mock_Consumer.return_value = kafkaConsumer
    msg = MagicMock()
    kafkaConsumer.poll.return_value = msg
    msg_code = MagicMock()
    msg_code.code.return_value = KafkaError._PARTITION_EOF
    msg.error.return_value = msg_code
    msg.topic.return_value = "test"

    # call
    topic = "test"
    bootstrap_servers = "test"
    kafkaMessageCounter.listen_for_message_and_process(topic, bootstrap_servers)

    # check
    expected_calls = [
        call("Topic test [1] reached end at offset 1\n"),
        call("0: Disconnected from Kafka topic, reconnecting..."),
    ]
    kafkaMessageCounter.process_message.assert_not_called()
    mock_logging.warning.assert_has_calls(expected_calls)
    kafkaConsumer.close.assert_called_once()


@patch("addons.images.count_metric.kafka_counter.logging")
@patch("addons.images.count_metric.kafka_counter.Consumer")
def test_listen_for_message_and_process_error(mock_Consumer, mock_logging):
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    proc_msg = MagicMock()
    proc_msg.side_effect = [True, False]
    kafkaMessageCounter.should_run = proc_msg
    kafkaMessageCounter.process_message = MagicMock()

    kafkaConsumer = MagicMock()
    mock_Consumer.return_value = kafkaConsumer
    msg = MagicMock()
    kafkaConsumer.poll.return_value = msg
    msg_code = MagicMock()
    msg_code.code.return_value = None
    msg.error.return_value = msg_code

    # call and verify it raises the exception
    with pytest.raises(KafkaException):
        topic = "test"
        bootstrap_servers = "test"
        kafkaMessageCounter.listen_for_message_and_process(topic, bootstrap_servers)

    kafkaMessageCounter.process_message.assert_not_called()
    mock_logging.warning.assert_called_with(
        "0: Disconnected from Kafka topic, reconnecting..."
    )
    kafkaConsumer.close.assert_called_once()


def test_get_topic_from_type_success():
    # prepare
    kafkaMessageCounterType0 = createKafkaMessageCounter(0)
    kafkaMessageCounterType1 = createKafkaMessageCounter(1)

    # call
    topicType0 = kafkaMessageCounterType0.get_topic_from_type()
    topicType1 = kafkaMessageCounterType1.get_topic_from_type()

    # check
    messageType = "bsm"
    expectedTopicType0 = f"topic.OdeRawEncoded{messageType.upper()}Json"
    expectedTopicType1 = f"topic.Ode{messageType.capitalize()}Json"
    assert topicType0 == expectedTopicType0
    assert topicType1 == expectedTopicType1


# # PROBLEM TEST
def test_read_topic_success():
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafkaMessageCounter.get_topic_from_type = MagicMock()
    kafkaMessageCounter.get_topic_from_type.return_value = "test"
    os.environ["ODE_KAFKA_BROKERS"] = "test"
    kafkaMessageCounter.listen_for_message_and_process = MagicMock()
    kafkaMessageCounter.should_run = MagicMock()
    kafkaMessageCounter.should_run.side_effect = [True, False]

    # call
    kafkaMessageCounter.read_topic()

    # check
    kafkaMessageCounter.get_topic_from_type.assert_called_once()
    kafkaMessageCounter.listen_for_message_and_process.assert_called_once_with(
        "test", "test"
    )


def test_start_counter_success():
    # prepare
    kafkaMessageCounter = createKafkaMessageCounter(0)
    kafka_counter.logging = MagicMock()
    kafka_counter.logging.info = MagicMock()
    kafka_counter.BackgroundScheduler = MagicMock()
    kafka_counter.BackgroundScheduler.return_value = MagicMock()
    kafka_counter.BackgroundScheduler.return_value.add_job = MagicMock()
    kafka_counter.BackgroundScheduler.return_value.start = MagicMock()
    kafkaMessageCounter.read_topic = MagicMock()

    # call
    kafkaMessageCounter.start_counter()

    # check
    kafka_counter.BackgroundScheduler.assert_called_once()
    kafka_counter.BackgroundScheduler.return_value.add_job.assert_called_once()
    kafka_counter.BackgroundScheduler.return_value.start.assert_called_once()
    kafka_counter.logging.info.assert_called_once()
    kafkaMessageCounter.read_topic.assert_called_once()
