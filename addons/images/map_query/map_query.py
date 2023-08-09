import os
import logging
import json
import kafka_helper
import hashlib
from datetime import datetime
from pymongo import MongoClient


collection = None

FRESHNESS_THRESHOLD = 60.0 # minutes

hashmap = {}
last_cleanup = datetime.now()

def set_mongo_collection():
  global collection
  client = MongoClient(os.getenv("MONGO_DB_URI"))
  db = client[os.getenv("MONGO_DB_NAME")]
  collection = db[os.getenv("MONGO_MAP_OUTPUT_COLLECTION")]
  return collection

# Clean up the hashmap by removing old records
def cleanup_hashmap():
  global hashmap
  tempmap = {}
  currenttime = datetime.now()
  for key, val in hashmap.items():
    # If the time since the element was last pushed to Pub/Sub is less than the threshold, keep it
    if ((currenttime - val).total_seconds() / 60.0) < FRESHNESS_THRESHOLD:
      tempmap[key] = val

  hashmap = tempmap

def process_message(msg):
  writeTopic = False
  hash_string = ''
  
  try:
    json_msg = json.loads(msg.value.decode('utf8'))

    # Check if malformed ProcessedMap
    processed_map_parts = ['mapFeatureCollection', 'connectingLanesFeatureCollection', 'properties']
    if not all([part in processed_map_parts for part in json_msg]):
      logging.warning('Malformed ProcessedMap detected, missing at least one expected part:')
      logging.warning(json_msg)
      return
    
    # Remove ODE and GeoJsonConverter generated timestamps
    del json_msg['properties']['odeReceivedAt']
    del json_msg['properties']['timeStamp']

    # Create hashkey from message data
    hash_string = hashlib.sha256(str(json_msg).encode('utf-8')).hexdigest()
    current_time = datetime.now()
    # Check if hashkey exists already for a duplicate message check
    if hash_string in hashmap:
      # Check if the value of the hashstring is from an hour or more in the past
      time_diff = (datetime.now() - hashmap[hash_string]).total_seconds() / 60.0

      if time_diff >= FRESHNESS_THRESHOLD:
        hashmap[hash_string] = current_time
        writeTopic = True
    else:
      # Cleanup the hashmap if a cleanup has not occurred in 60 minutes
      global last_cleanup
      if ((datetime.now() - last_cleanup).total_seconds() / 60.0) >= FRESHNESS_THRESHOLD:
        cleanup_hashmap()
        last_cleanup = datetime.now()

      hashmap[hash_string] = current_time
      writeTopic = True
  except Exception as e:
    logging.error('A ProcessedMap message failed to be processed with the following error: ' + str(e))

  try:
    if writeTopic:
      json_msg = json.loads(msg.value.decode('utf8'))
      logging.info('New record candidate from ' + json_msg['properties']['originIp'])
      insert_map_msg(json_msg)
  except Exception as e:
    logging.error('A ProcessedMap message failed to be written to Kafka: ' + str(e))
    logging.error('Reverting datastore and dictionary for {}...'.format(hash_string))
    del hashmap[hash_string]

def insert_map_msg(map_msg):
  global collection
  if collection is None:
    logging.debug("setting mongo client")
    collection = set_mongo_collection()
  collection.insert_one(map_msg)

def start_consumers(reconnect_if_disconnected=True):
  topic = os.getenv('DEDUP_INPUT_TOPIC')
  
  while True:
    logging.debug(f'Listening for messages on Kafka topic {topic}...')
    consumer = kafka_helper.create_consumer(topic)
    for msg in consumer:
      process_message(msg)

    # Break if not set to reconnect (mainly for unit test purposes)
    if not reconnect_if_disconnected:
      break


if __name__ == "__main__":
  log_level = 'INFO' if "LOGGING_LEVEL" not in os.environ else os.environ['LOGGING_LEVEL'] 
  logging.basicConfig(format='%(levelname)s:%(message)s', level=log_level)
  
  db_type = os.getenv("DB_TYPE","BIGQUERY").upper()
  logging.info(f"Starting MapInfo service for: {db_type}")

  start_consumers()