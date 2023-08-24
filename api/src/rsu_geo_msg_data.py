from google.cloud import bigquery
import util
import os
import logging
from datetime import datetime
from pymongo import MongoClient, DESCENDING, TEXT, GEOSPHERE

coord_resolution = 0.0001  # lats more than this are considered different
time_resolution = 25  # time deltas bigger than this are considered different


def msg_hash(ip, msg_type, timestamp, long, lat):
    return (
        ip
        + msg_type
        + "_"
        + str(int(timestamp / time_resolution))
        + "_"
        + str(int(long / coord_resolution))
        + "_"
        + str(int(lat / coord_resolution))
    )


def query_geo_data_mongo(pointList, start, end, msg_type):
    logging.debug("query_geo_data_mongo")
    start_date = util.format_date_utc(start, "DATETIME")
    end_date = util.format_date_utc(end, "DATETIME")

    try:
        client = MongoClient(os.getenv("MONGO_DB_URI"), serverSelectionTimeoutMS=5000, maxPoolSize=10)
        db = client.get_database(os.getenv("MONGO_DB_NAME"))
        collection = db[os.getenv("MONGO_GEO_COLLECTION")]
        logging.debug("connection to MongoDB successfully established")
    except Exception as e:
        logging.error(f"Failed to connect to Mongo counts collection with error message: {e}")
        return [], 503

    filter = {
        "properties.msg_type": msg_type,
        "properties.timestamp": {"$gte": start_date, "$lte": end_date},
        "geometry": {"$geoWithin": {"$geometry": {"type": "Polygon", "coordinates": [pointList]}}},
    }
    hashmap = {}
    count = 0
    total_count = 0

    try:
        logging.info(f"Running filter: {filter} on mongo collection {os.getenv('MONGO_GEO_COLLECTION')}")
        collection.create_index([("properties.msg_type", TEXT), 
                                 ("properties.timestamp", DESCENDING), 
                                 ("geometry", GEOSPHERE)])
        for doc in collection.find(filter=filter):
            message_hash = msg_hash(
                msg_type,
                doc["properties"]["id"],
                int(datetime.timestamp(doc["properties"]["timestamp"])),
                doc["geometry"]["coordinates"][0],
                doc["geometry"]["coordinates"][1],
            )

            if message_hash not in hashmap:
                doc["properties"]["time"] = doc["properties"]["timestamp"].strftime("%Y-%m-%dT%H:%M:%SZ")
                doc.pop("_id")
                doc["properties"].pop("timestamp")
                hashmap[message_hash] = doc
                count += 1
                total_count += 1
            else:
                total_count += 1

        logging.info(f"Filter successful. Records returned: {count}, Total records: {total_count}")
        client.close()
        return list(hashmap.values()), 200
    except Exception as e:
        logging.error(f"Filter failed: {e}")
        return [], 500


def query_bsm_data_bq(pointList, start, end):
    start_date = util.format_date_utc(start)
    end_date = util.format_date_utc(end)
    client = bigquery.Client()
    tablename = os.environ["BSM_DB_NAME"]
    geogString = "POLYGON(("
    for elem in pointList:
        long = str(elem.pop(0))
        lat = str(elem.pop(0))
        geogString += long + " " + lat + ","

    geogString = geogString[:-1] + "))"

    query = (
        "SELECT DISTINCT bsm.metadata.originIp as Ip, "
        f"bsm.payload.data.coreData.position.longitude as long, "
        f"bsm.payload.data.coreData.position.latitude as lat, "
        f"bsm.metadata.odeReceivedAt as time "
        f"FROM `{tablename}` "
        f'WHERE TIMESTAMP(bsm.metadata.odeReceivedAt) >= TIMESTAMP("{start_date}") '
        f'AND TIMESTAMP(bsm.metadata.odeReceivedAt) <= TIMESTAMP("{end_date}") '
        f"AND ST_CONTAINS(ST_GEOGFROM('{geogString}'), "
        f"ST_GEOGPOINT(bsm.payload.data.coreData.position.longitude, bsm.payload.data.coreData.position.latitude))"
    )

    logging.info(f"Running query on table {tablename}")

    query_job = client.query(query)
    hashmap = {}
    count = 0
    total_count = 0

    for row in query_job:
        message_hash = msg_hash(
            "Bsm",
            row["Ip"],
            int(datetime.timestamp(util.format_date_utc(row["time"], "DATETIME"))),
            row["long"],
            row["lat"],
        )

        if message_hash not in hashmap:
            doc = {
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [row["long"], row["lat"]]},
                "properties": {
                    "id": row["Ip"],
                    "time": util.format_date_utc(row["time"], "STRING", True),
                },
            }
            hashmap[message_hash] = doc
            count += 1
            total_count += 1
        else:
            total_count += 1

    logging.info(f"Query successful. Record returned: {count}")
    return list(hashmap.values()), 200


# REST endpoint resource class and schema
from flask import request
from flask_restful import Resource
from marshmallow import Schema, fields


class RsuGeoMsgDataSchema(Schema):
    geometry = fields.String(required=False)
    start = fields.DateTime(required=False)
    end = fields.DateTime(required=False)
    msg_type = fields.String(required=False)


class RsuGeoMsgData(Resource):
    options_headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Content-Type,Authorization",
        "Access-Control-Allow-Methods": "POST",
        "Access-Control-Max-Age": "3600",
    }

    headers = {"Access-Control-Allow-Origin": "*", "Content-Type": "application/json"}

    def options(self):
        # CORS support
        return ("", 204, self.options_headers)

    def post(self):
        logging.debug("RsuGeoMsgData POST requested")

        # Get arguments from request
        try:
            data = request.json
            msg_type = data["msg_type"]
            pointList = data["geometry"]
            start = data["start"]
            end = data["end"]
        except:
            return (
                'Body format: {"start": string, "end": string, "geometry": coordinate list, "msg_type": string}',
                400,
                self.headers,
            )
        db_type = os.getenv("COUNTS_DB_TYPE", "BIGQUERY").upper()
        data = []
        code = None

        if db_type == "BIGQUERY":
            if msg_type.upper() == "BSM":
                logging.debug("RsuBsmData BigQuery Bsm query")
                data, code = query_bsm_data_bq(pointList, start, end)
            else:
                code = 400
                logging.error(f"RsuBsmData BigQuery message type:{msg_type.upper()} not supported!")
        elif db_type == "MONGODB":
            logging.debug(f"RsuBsmData Mongodb {msg_type.capitalize()} query")
            data, code = query_geo_data_mongo(pointList, start, end, msg_type.capitalize())

        return (data, code, self.headers)
