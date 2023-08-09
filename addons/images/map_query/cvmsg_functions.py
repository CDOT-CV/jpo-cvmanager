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
    dy_deg = (y * 0.01)/111111.0
    
    # calculate the easting offset in decimal degrees
    # deg/m dependent on latitude
    dx_deg = (x * 0.01)/(math.cos((math.pi/180.0) * reference_point[1]) * 111111.0)

    return (reference_point[0] + dx_deg, reference_point[1] + dy_deg)
    

def map_intersection_geometry_to_geojson (ip, intersectionGeometry):
    """
    Builds a geoJSON representation of the intersection data in a MAP message.  
    
    Parameters:
        intersectionGeometry (dict): The intersectionGeometry of a J2735 standard MAP message.

    Returns:
        geojson (dict): Intersection geometry data extracted and repackaged into a geoJSON data structure.
    """
    # create new python dictionary to hold geojson data for this intersection
    geojson = {'type': 'FeatureCollection', 'features': []}
    
    # extract the reference point for this intersection
    ref_lat = intersectionGeometry['refPoint']['latitude']
    ref_lon = intersectionGeometry['refPoint']['longitude']
    
    
    # loop through each lane in the laneSet and build a geoJSON feature for each lane
    for lane in intersectionGeometry['laneSet']['GenericLane']:
        
        anchor_lat = ref_lat
        anchor_lon = ref_lon
        
        # create a feature template to fill in 
        feature = {'type': 'Feature',
                    'properties': {},
                    'geometry': {'type': 'LineString', 
                                 'coordinates': []}}
        
        # extract properties for the lane
        # laneID
        props = {'laneID': lane['laneID']}
        
        # egress/ingress Path
        props.update({'ingressPath': "true" if lane['laneAttributes']['directionalUse']['ingressPath'] else "false"})
        props.update({'egressPath': "true" if lane['laneAttributes']['directionalUse']['egressPath'] else "false"})
            
        # egress/ingress Approach
        props.update({'egressApproach': lane['egressApproach'] if lane['egressApproach'] is not None else 0})
        props.update({'ingressApproach': lane['ingressApproach'] if lane['ingressApproach'] is not None else 0})     

        props.update({'ip': ip})
        
        # connecting lanes a list
        connected_lanes = []
        for connectingLane in lane['connectsTo']['connectsTo']:
            connected_lanes.append(connectingLane['connectingLane']['lane'])
        props.update({'connectedLanes': connected_lanes})
            
        # add the properties into our lane feature 
        feature['properties'] = props
        
        # extract the path coordinates for the lane 
        for node in lane['nodeList']['nodes']['NodeXY']:
            for deltatype, deltavalue in node['delta'].items():
                # nodeLatLon
                if deltatype == 'nodeLatLon':
                    if deltavalue['lat'] is not None:
                        # complete absolute lat-long representation per J2735 
                        # lat-long values expressed in standard SAE 1/10 of a microdegree
                        c = [deltavalue['lon']/1.0E7, deltavalue['lat']/1.0E7]
                        feature['geometry']['coordinates'].append(c)
                        # reset the anchor point for following offset nodes
                        # J2735 is not clear if only one of these nodelatlon types is allowed in the lane path nodes
                        anchor_lat = deltavalue['lat']
                        anchor_lon = deltavalue['lon']
                # nodeXY
                else:
                    if deltavalue['x'] is not None:
                        # calculate offset lon,lat values
                        offset_lon_lat = node_xy_to_lon_lat(reference_point=(anchor_lon, anchor_lat), x=deltavalue['x'], y=deltavalue['y'])
                        c = [offset_lon_lat[0], offset_lon_lat[1]]
                        feature['geometry']['coordinates'].append(c)
                        # reset the anchor point for the next offset node
                        # assumes there is never more than one xy node type within a single node definition
                        # more than one xy node type within a single node definition is a MAP message configuration error
                        anchor_lat = offset_lon_lat[1]
                        anchor_lon = offset_lon_lat[0]
                        
        # add this lane feature to the list of features in our intersection 
        geojson['features'].append(feature)
    
    return geojson