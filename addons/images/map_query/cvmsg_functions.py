import math


def node_xy_to_lon_lat(reference_point=(0.0, 0.0), x=0.0, y=0.0):
    """
    Returns the longitude and latitude of the given offset location in x,y meters from the
    given reference point. This function uses the quick, approximate spherical longitude
    latitude calculation.

    Parameters:
        reference_point (float tuple): Reference point for x,y distance must be in decimal degrees in (longitude, latitude) order.
        x (float): Easting distance from reference point in centimeters.
        y (float): Northing distance from reference point in centimeters.

    Returns:
        location_point (float tuple): Calculated (longitude, latitude) of the given x,y offset location.
    """
    # calculate the northing offset in decimal degrees
    dy_deg = (y * 0.01) / 111111.0

    # calculate the easting offset in decimal degrees
    # deg/m dependent on latitude
    dx_deg = (x * 0.01) / (math.cos((math.pi / 180.0) * reference_point[1]) * 111111.0)

    return (reference_point[0] + dx_deg, reference_point[1] + dy_deg)


def map_intersection_geometry_to_geojson(ip, intersectionGeometry):
    """
    Builds a geoJSON representation of the intersection data in a MAP message.

    Parameters:
        intersectionGeometry (dict): The intersectionGeometry of a J2735 standard MAP message.

    Returns:
        geojson (dict): Intersection geometry data extracted and repackaged into a geoJSON data structure.
    """
    # create new python dictionary to hold geojson data for this intersection
    geojson = {"type": "FeatureCollection", "features": []}

    # extract the reference point for this intersection
    ref_lat = intersectionGeometry.get("refPoint", {}).get("latitude", 0)
    ref_lon = intersectionGeometry.get("refPoint", {}).get("longitude", 0)

    # loop through each lane in the laneSet and build a geoJSON feature for each lane
    for lane in intersectionGeometry.get("laneSet", {}).get("GenericLane", []):
        anchor_lat = ref_lat
        anchor_lon = ref_lon

        # create a feature template to fill in
        feature = {
            "type": "Feature",
            "properties": {},
            "geometry": {"type": "LineString", "coordinates": []},
        }

        # extract properties for the lane
        props = {
            "laneID": lane.get("laneID", ""),
            "ingressPath": "true"
            if lane.get("laneAttributes", {})
            .get("directionalUse", {})
            .get("ingressPath", False)
            else "false",
            "egressPath": "true"
            if lane.get("laneAttributes", {})
            .get("directionalUse", {})
            .get("egressPath", False)
            else "false",
            "egressApproach": lane.get("egressApproach", 0),
            "ingressApproach": lane.get("ingressApproach", 0),
            "ip": ip,
            "connectedLanes": [
                conn["connectingLane"]["lane"]
                for conn in lane.get("connectsTo", {}).get("connectsTo", [])
            ],
        }

        # add the properties into our lane feature
        feature["properties"] = props

        # extract the path coordinates for the lane
        for node in lane.get("nodeList", {}).get("nodes", {}).get("NodeXY", []):
            for deltatype, deltavalue in node["delta"].items():
                if deltatype == "nodeLatLon":
                    if deltavalue.get("lat") is not None:
                        # complete absolute lat-long representation per J2735
                        c = [deltavalue["lon"] / 1.0e7, deltavalue["lat"] / 1.0e7]
                        feature["geometry"]["coordinates"].append(c)
                        anchor_lat = deltavalue["lat"]
                        anchor_lon = deltavalue["lon"]
                else:
                    if deltavalue.get("x") is not None:
                        offset_lon_lat = node_xy_to_lon_lat(
                            reference_point=(anchor_lon, anchor_lat),
                            x=deltavalue["x"],
                            y=deltavalue["y"],
                        )
                        c = [offset_lon_lat[0], offset_lon_lat[1]]
                        feature["geometry"]["coordinates"].append(c)
                        anchor_lat = offset_lon_lat[1]
                        anchor_lon = offset_lon_lat[0]

        # add this lane feature to the list of features in our intersection
        geojson["features"].append(feature)

    return geojson
