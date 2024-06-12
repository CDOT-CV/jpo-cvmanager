import paho.mqtt.client as mqtt
import json
import pgquery_rsu
import logging
import os

log_level = 'INFO' if "LOGGING_LEVEL" not in os.environ else os.environ['LOGGING_LEVEL']
logging.basicConfig(format='%(levelname)s:%(message)s', level=log_level)

# Configuration
MQTT_BROKER_HOST = "10.0.0.79"
MQTT_BROKER_PORT = 1883  # Standard MQTT port
MQTT_TOPIC_BASE = "cisco/edge-intelligence/njdot/telemetry/stream/J2735/"
MQTT_V2X_TOPIC_LIST = ["MAP", "SPAT", "TIM"]

def find_rsu_id(rsu_ip):
    global rsu_items
    for rsu in rsu_items:
        if rsu['rsu_ip'] == rsu_ip:
            return rsu['rsu_id'] 
    return None

# Function to process an incoming MQTT message
def on_message(client, userdata, message):
    try:
        payload = json.loads(message.payload.decode('utf-8'))
        if 'rsuId' not in payload or 'ts' not in payload:
            raise ValueError("Invalid message format")
        rsu_ip = payload['rsuId']
        timestamp = payload['ts']
        rsu_id = find_rsu_id(rsu_ip)
        
        ping_msg = {
            "rsu_id": rsu_id,
            "result": 1,
            "timestamp": timestamp
        }

        logging.debug(f"Received message for RSU {payload['rsuId']}: {timestamp}")

        pgquery_rsu.insert_rsu_ping_mqtt(ping_msg)
    except (ValueError, KeyError) as e:
        print(f"Error processing message: {e}")

# Connect to MQTT broker
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
client.on_message = on_message
client.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT)

# Fetch device IDs from the database
rsu_items = pgquery_rsu.get_rsu_data()
# rsu_prefix_list = []

# for rsu_id in rsu_items:
#     topic_prefix = rsu_id["rsu_ip"].split("-")[0]
#     rsu_prefix_list.append(topic_prefix)
    


# Subscribe to relevant MQTT topics
for type in MQTT_V2X_TOPIC_LIST:
    topic = MQTT_TOPIC_BASE + type + "/" + "#"
    logging.debug(f"Subscribing to topic: {topic}")
    client.subscribe(topic)

client.loop_forever() 

