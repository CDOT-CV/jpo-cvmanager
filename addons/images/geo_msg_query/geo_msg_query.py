import os
from concurrent.futures import ThreadPoolExecutor
import logging
import json
from pymongo import MongoClient, DESCENDING, TEXT
from datetime import datetime
import kafka_helper


def set_mongo_client(MONGO_DB_URI, MONGO_DB, MONGO_COLLECTION):
    client = MongoClient(MONGO_DB_URI)
    db = client[MONGO_DB]
    collection = db[MONGO_COLLECTION]
    return db, collection


def create_collection_and_indexes(db, collection_name, MONGO_TTL):
    if collection_name not in db.list_collection_names():
        db.create_collection(collection_name)
        logging.info("Collection created.")

        collection = db.get_collection(collection_name)
        collection.create_index([("geometry", "2dsphere"), ("properties.timestamp", DESCENDING)])
        logging.info("Complex index created.")

        collection.create_index([("properties.msg_type", "text")])
        logging.info("Message Type index created.")

        # Create a TTL index on the timestamp field with a 30-day expiration
        collection.create_index([("properties.timestamp", DESCENDING)], expireAfterSeconds=MONGO_TTL * 24 * 60 * 60)
        logging.info("TTL index created.")
    else:
        logging.info("Database Already Exists")


def create_message(original_message, msg_type):
    latitude = None
    longitude = None
    if msg_type == "Bsm":
        longitude = original_message["payload"]["data"]["coreData"]["position"]["longitude"]
        latitude = original_message["payload"]["data"]["coreData"]["position"]["latitude"]
    elif msg_type == "Psm":
        longitude = original_message["payload"]["data"]["position"]["longitude"]
        latitude = original_message["payload"]["data"]["position"]["latitude"]
    if latitude and longitude:
        timestamp_str = original_message["metadata"]["odeReceivedAt"]
        if len(timestamp_str) > 26:
                timestamp_str = timestamp_str[:26] + "Z"
        new_message = {
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [
                    longitude,
                    latitude,
                ],
            },
            "properties": {
                "id": original_message["metadata"]["originIp"],
                "timestamp": datetime.strptime(
                        timestamp_str,
                        "%Y-%m-%dT%H:%M:%S.%fZ",
                    ),
                "msg_type": msg_type,
            },
        }
        return new_message
    else:
        logging.warn(f"create_message: Could not create a message for type: {msg_type}")
        return None


def process_message(message, collection, count, msg_type):
    try:
        json_msg = json.loads(message.value.decode("utf8"))
        new_message = create_message(json_msg, msg_type)
        if new_message:
            collection.insert_one(new_message)
            logging.debug(f"Message {count} uploaded")
        if count % 100 == 0 and count != 0:
            logging.info(f"Processed {count} {msg_type} messages.")
    except Exception as e:
        logging.error(f"process_message: message {count} failed to be processed with the following error: " + str(e))


def process_topic(msg_type, collection, executor):
    msg_type = msg_type.capitalize()
    logging.info(f"Starting {msg_type} processing service.")
    count = 0
    topic = f"topic.Ode{msg_type}Json"
    logging.info(f"Listening for messages on Kafka topic {topic}...")
    consumer = kafka_helper.create_consumer(topic)
    for msg in consumer:
        logging.debug(f"Message {count} started")
        executor.submit(process_message, msg, collection, count, msg_type)
        count += 1


def run():
    MSG_TYPES = os.getenv("GEO_MSG_TYPES")
    MONGO_DB_URI = os.getenv("MONGO_DB_URI")
    MONGO_DB = os.getenv("MONGO_DB_NAME")
    MONGO_GEO_COLLECTION = os.getenv("MONGO_GEO_COLLECTION")
    MONGO_TTL = int(os.getenv("MONGO_TTL"))

    if (
        MSG_TYPES is None
        or MONGO_DB_URI is None
        or MONGO_DB is None
        or MONGO_GEO_COLLECTION is None
        or MONGO_TTL is None
    ):
        logging.error("Environment variables are not set! Exiting.")
        exit("Environment variables are not set! Exiting.")

    message_types = json.loads(os.getenv("GEO_MSG_TYPES"))
    executor = ThreadPoolExecutor(max_workers=5)

    db, collection = set_mongo_client(MONGO_DB_URI, MONGO_DB, MONGO_GEO_COLLECTION)
    create_collection_and_indexes(db, MONGO_GEO_COLLECTION, MONGO_TTL)

    futures = []
    for msg_type in message_types:
        future = executor.submit(process_topic, msg_type, collection, executor)
        futures.append(future)

    # Wait for all futures to complete
    for future in futures:
        future.result()


if __name__ == "__main__":
    # Configure logging based on ENV var or use default if not set
    log_level = "INFO" if "LOGGING_LEVEL" not in os.environ else os.environ["LOGGING_LEVEL"]
    logging.basicConfig(format="%(levelname)s:%(message)s", level=log_level)
    run()
