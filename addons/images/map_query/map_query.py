import os
import logging
import json
import kafka_helper
import hashlib
from datetime import datetime
import sqlalchemy
# from cvmsg_functions import map_intersection_geometry_to_geojson

db_config = {
    # Pool size is the maximum number of permanent connections to keep.
    "pool_size": 5,
    # Temporarily exceeds the set pool_size if no connections are available.
    "max_overflow": 2,
    # Maximum number of seconds to wait when retrieving a
    # new connection from the pool. After the specified amount of time, an
    # exception will be thrown.
    "pool_timeout": 30,  # 30 seconds
    # 'pool_recycle' is the maximum number of seconds a connection can persist.
    # Connections that live longer than the specified amount of time will be
    # reestablished
    "pool_recycle": 60,  # 1 minutes
}

db = None

FRESHNESS_THRESHOLD = 1.0  # minutes

hashmap = {}
last_cleanup = datetime.now()


def init_tcp_connection_engine():
    db_user = os.environ["DB_USER"]
    db_pass = os.environ["DB_PASS"]
    db_name = os.environ["DB_NAME"]
    db_host = os.environ["DB_HOST"]

    # Extract host and port from db_host
    host_args = db_host.split(":")
    db_hostname, db_port = host_args[0], int(host_args[1])

    logging.info(f"Creating DB pool to {db_hostname}:{db_port}")
    pool = sqlalchemy.create_engine(
        # Equivalent URL:
        # postgresql+pg8000://<db_user>:<db_pass>@<db_host>:<db_port>/<db_name>
        sqlalchemy.engine.url.URL.create(
            drivername="postgresql+pg8000",
            username=db_user,  # e.g. "my-database-user"
            password=db_pass,  # e.g. "my-database-password"
            host=db_hostname,  # e.g. "127.0.0.1"
            port=db_port,  # e.g. 5432
            database=db_name,  # e.g. "my-database-name"
        ),
        **db_config,
    )

    pool.dialect.description_encoding = None
    logging.info("DB pool created!")
    return pool


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
    hash_string = ""

    try:
        json_msg = json.loads(msg.value.decode("utf8"))

        # Check if malformed ProcessedMap
        processed_map_parts = ["mapFeatureCollection", "connectingLanesFeatureCollection", "properties"]
        if not all([part in processed_map_parts for part in json_msg]):
            logging.warning("Malformed ProcessedMap detected, missing at least one expected part:")
            logging.warning(json_msg)
            return

        # Remove ODE and GeoJsonConverter generated timestamps
        del json_msg["properties"]["odeReceivedAt"]
        del json_msg["properties"]["timeStamp"]

        # Create hashkey from message data
        hash_string = hashlib.sha256(str(json_msg).encode("utf-8")).hexdigest()
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
        logging.error("A ProcessedMap message failed to be processed with the following error: " + str(e))

    try:
        if writeTopic:
            json_msg = json.loads(msg.value.decode("utf8"))
            logging.info(f'New record candidate from {json_msg["properties"]["originIp"]}')
            insert_map_msg(json_msg)
    except Exception as e:
        logging.error("A ProcessedMap message failed to be written to Kafka: " + str(e))


def insert_map_msg(map_msg):
    global db
    if db is None:
        db = init_tcp_connection_engine()
    logging.info("insert_map_msg")
    query = f"INSERT INTO public.map_info (ipv4_address, geojson, date) VALUES "
    with db.connect() as conn:
        ip = map_msg["properties"]["originIp"]
        time = map_msg["properties"]["odeReceivedAt"]
        feature = map_msg["mapFeatureCollection"]
        featureStr = json.dumps(feature)
        logging.info(f"{ip}:{time}")
        query += f"('{ip}', '{featureStr}', '{time}'), "
        query = (
            query[:-2] + f" ON CONFLICT(ipv4_address) DO UPDATE SET (geojson, date) = (EXCLUDED.geojson, EXCLUDED.date)"
        )
        try:
            # logging.info(query)
            conn.execute(query)
            return f"update successful"
        except Exception as e:
            logging.exception(f"Error inserting MapInfo record: {e}")
            return f"error inserting MapInfo row(s)"


def start_consumers(reconnect_if_disconnected=True):
    topic = os.getenv("INPUT_TOPIC")

    while True:
        logging.debug(f"Listening for messages on Kafka topic {topic}...")
        consumer = kafka_helper.create_consumer(topic)
        for msg in consumer:
            process_message(msg)

        # Break if not set to reconnect (mainly for unit test purposes)
        if not reconnect_if_disconnected:
            break


if __name__ == "__main__":
    log_level = "INFO" if "LOGGING_LEVEL" not in os.environ else os.environ["LOGGING_LEVEL"]
    logging.basicConfig(format="%(levelname)s:%(message)s", level=log_level)

    db_type = os.getenv("DB_TYPE", "BIGQUERY").upper()
    logging.info(f"Starting MapInfo service for: {db_type}")

    start_consumers()
