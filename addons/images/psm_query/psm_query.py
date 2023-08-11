import os
from concurrent.futures import ThreadPoolExecutor
import logging
from pymongo import MongoClient
from datetime import datetime


def set_mongo_client(MONGO_DB_URI, MONGO_DB, MONGO_PSM_INPUT_COLLECTION):
    client = MongoClient(MONGO_DB_URI)
    db = client[MONGO_DB]
    collection = db[MONGO_PSM_INPUT_COLLECTION]
    return db, collection


def create_message(original_message):
    new_message = {
        "type": "Feature",
        "geometry": {
            "type": "Point",
            "coordinates": [
                original_message["payload"]["data"]["position"]["longitude"],
                original_message["payload"]["data"]["position"]["latitude"],
            ],
        },
        "properties": {
            "id": original_message["metadata"]["originIp"],
            "timestamp": datetime.strptime(
                original_message["metadata"]["odeReceivedAt"], "%Y-%m-%dT%H:%M:%S.%fZ"
            ),
        },
    }
    return new_message


def process_message(message, db, collection):
    new_message = create_message(message)
    db[collection].insert_one(new_message)


def run():
    MONGO_DB_URI = os.getenv("MONGO_DB_URI")
    MONGO_DB = os.getenv("MONGO_DB_NAME")
    MONGO_PSM_INPUT_COLLECTION = os.getenv("MONGO_PSM_INPUT_COLLECTION")
    MONGO_GEO_OUTPUT_COLLECTION = os.getenv("MONGO_GEO_OUTPUT_COLLECTION")

    if (
        MONGO_DB_URI is None
        or MONGO_PSM_INPUT_COLLECTION is None
        or MONGO_DB is None
        or MONGO_GEO_OUTPUT_COLLECTION is None
    ):
        logging.error("Environment variables are not set! Exiting.")
        exit("Environment variables are not set! Exiting.")

    executor = ThreadPoolExecutor(max_workers=5)

    db, collection = set_mongo_client(
        MONGO_DB_URI, MONGO_DB, MONGO_PSM_INPUT_COLLECTION
    )

    count = 0
    with collection.watch() as stream:
        logging.info(
            f"Starting PSM Query service MongoDB watcher on collection: {MONGO_PSM_INPUT_COLLECTION}"
        )
        for change in stream:
            count += 1
            executor.submit(
                process_message, change["fullDocument"], db, MONGO_GEO_OUTPUT_COLLECTION
            )
            logging.info(count)


if __name__ == "__main__":
    # Configure logging based on ENV var or use default if not set
    log_level = (
        "INFO" if "LOGGING_LEVEL" not in os.environ else os.environ["LOGGING_LEVEL"]
    )
    logging.basicConfig(format="%(levelname)s:%(message)s", level=log_level)
    run()
