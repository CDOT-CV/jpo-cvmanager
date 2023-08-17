import multidict
import datetime

##################################### request_data ###########################################
geometry = [[
                    -104.891699,
                    39.563912
                ]]

request_params_bsm = {
    "msg_type": "BSM",
    "geometry": geometry,
    "start": "start_date",
    "end": "end_date",
}

request_params_psm = {
    "msg_type": "PSM",
    "geometry": geometry,
    "start": "start_date",
    "end": "end_date",
}

###################################### Sample Data ##########################################

point_list = [10.000, -10.000]

mongo_bsm_data_response = [
    {
        "_id": "bson_id",
        "type": "Feature",
        "properties": {"id": "8.8.8.8", "timestamp": datetime.datetime.utcnow()},
        "geometry": {"type": "Point", "coordinates": point_list},
    }
]

processed_bsm_message_data = [
    {
        "type": "Feature",
        "properties": {
            "id": "8.8.8.8",
            "time": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        },
        "geometry": {"type": "Point", "coordinates": point_list},
    }
]

bq_bsm_data_response = [
    {
        "Ip": "8.8.8.8",
        "long": point_list[0],
        "lat": point_list[1],
        "time": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    },
]
