from images.map_query import cvmsg_functions


def test_node_xy_to_lon_lat():
    # prepare
    reference_point = (5.8, 6.8)
    x = 2.3
    y = 3.4

    # execute
    result = cvmsg_functions.node_xy_to_lon_lat(reference_point, x, y)

    # verify
    expected_result = (5.800000208466664, 6.800000306000306)
    assert result == expected_result


def test_map_intersection_geometry_to_geojson():
    # prepare
    ip = "192.168.0.20"
    intersectionGeometry = {
        "refPoint": {"latitude": 6.8, "longitude": 5.8},
        "laneSet": {
            "GenericLane": [
                {
                    "laneID": 1,
                    "laneWidth": 2.3,
                    "nodeList": {
                        "nodes": {
                            "NodeXY": [
                                {"delta": {"nodeLatLon": {"lat": 6.8, "lon": 5.8}}},
                                {"delta": {"nodeXY": {"x": 2.3, "y": 3.4}}},
                            ]
                        }
                    },
                    "connectsTo": {"connectsTo": [{"connectingLane": {"lane": 2}}]},
                    "laneAttributes": {
                        "directionalUse": {"ingressPath": True, "egressPath": False}
                    },
                    "ingressApproach": 1,
                    "egressApproach": 2,
                }
            ]
        },
    }

    # execute
    result = cvmsg_functions.map_intersection_geometry_to_geojson(
        ip, intersectionGeometry
    )

    # verify
    expected_result = {
        "features": [
            {
                "geometry": {
                    "coordinates": [
                        [5.8e-07, 6.8e-07],
                        [5.800000208466664, 6.800000306000306],
                    ],
                    "type": "LineString",
                },
                "properties": {
                    "connectedLanes": [2],
                    "egressApproach": 2,
                    "egressPath": "false",
                    "ingressApproach": 1,
                    "ingressPath": "true",
                    "ip": "192.168.0.20",
                    "laneID": 1,
                },
                "type": "Feature",
            }
        ],
        "type": "FeatureCollection",
    }
    assert result == expected_result
