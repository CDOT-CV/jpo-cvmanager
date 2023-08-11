from kafka import KafkaConsumer
import json
import os


def create_consumer(topic):
    bootstrap_servers = os.environ["KAFKA_BROKERS"]

    if os.environ["CONFLUENT"] == "true":
        username = os.environ["CONFLUENT_KEY"]
        password = os.environ["CONFLUENT_SECRET"]
        consumer = KafkaConsumer(
            topic,
            group_id=f"{topic}-dedup",
            security_protocol="SASL_SSL",
            sasl_mechanism="PLAIN",
            sasl_plain_username=username,
            sasl_plain_password=password,
            bootstrap_servers=bootstrap_servers,
        )
    else:
        consumer = KafkaConsumer(
            topic, group_id=f"{topic}-dedup", bootstrap_servers=bootstrap_servers
        )

    return consumer
