from kafka import KafkaConsumer, KafkaProducer
import json
import os

def value_serializer(m):
  return json.dumps(m).encode('utf-8')

def create_producer():
  bootstrap_servers = os.environ['KAFKA_BROKERS']

  if os.environ['CONFLUENT'] == "true":
    username = os.environ['CONFLUENT_KEY']
    password = os.environ['CONFLUENT_SECRET']
    producer = KafkaProducer(bootstrap_servers = bootstrap_servers,
                            security_protocol='SASL_SSL',
                            sasl_mechanism='PLAIN',
                            sasl_plain_username=username,
                            sasl_plain_password=password,
                            value_serializer=value_serializer)
  else:
    producer = KafkaProducer(bootstrap_servers = bootstrap_servers,
                            value_serializer=value_serializer)
  
  return producer

def create_consumer(topic):
  bootstrap_servers = os.environ['KAFKA_BROKERS']

  if os.environ['CONFLUENT'] == "true":
    username = os.environ['CONFLUENT_KEY']
    password = os.environ['CONFLUENT_SECRET']
    consumer = KafkaConsumer(topic, 
                          group_id=f'{topic}-dedup', 
                          security_protocol='SASL_SSL',
                          sasl_mechanism='PLAIN',
                          sasl_plain_username=username,
                          sasl_plain_password=password,
                          bootstrap_servers=bootstrap_servers)
  else:
    consumer = KafkaConsumer(topic, 
                          group_id=f'{topic}-dedup', 
                          bootstrap_servers=bootstrap_servers)
  
  return consumer
