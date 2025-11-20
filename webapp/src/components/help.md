# Welcome to the {EnvironmentVars.DOT_NAME} CV Manager Website

This application helps organizations manage and monitor deployed RSUs, monitor and detect issues with connected intersections, and view numerous data types all in one application.

## Organizations and Profiles

This application uses organizations to manage permissions. All devices and users within the CV Manager are associated with 1 or more organizations. When using the CV Manager, you can only view one organization at a time. At the top right corner, you will see the User Profile menu. This menu opens to allow the user to change organizations, if they are a member or multiple, as well as logging out of the application.

<img src=../icons/help/organization_selection.png alt="Profile Dropdown" width="200"/>

**Note on feature flags**

Some features in the CV Manager can be enabled or disabled for each version. The feature flags set in this application are:

- ENABLE_RSU_FEATURES: ENABLED
  - Affects: RSU viewer, RSU status monitoring, RSU configuration
- ENABLE_INTERSECTION_FEATURES: ENABLED
  - Affects: Intersection map, Intersection dashboard, Conflict Monitor events, assessments, and notifications
- ENABLE_WZDX_FEATURES: DISABLED
  - Affects: WZDx message viewer
- ENABLE_MOOVE_AI_FEATURES: DISABLED
  - Affects: Moove AI hard braking event viewer

## [Map Dashboard](/dashboard/map)

The map dashboard is composed of a Mapbox map (background), the [Map Layer Menu](#map-layers) (red), and [RSU Status and Message Counts Menu](#rsu-status-and-message-counts) (green)

<img src=../icons/help/map_overview.png alt="Map Layer Selection Options" width="1000"/>

### Map Layers

The menu on the left side of the screen contains three main sections: Map Layers, RSU Filters, and RSU
Configuration. The Map Layers section allows users to change how data is visualized. The available layers are:

- RSU Viewer: display location and status of RSUs. Selecting an RSU will open a popup with status and health information, as well as opening a side panel for RSU configuration. The color of the RSU points indicates the status (configurable under "Filter RSUs"). Green means the RSU is online and actively reporting data, yellow indicates the RSU is currently offline but was recently active, and red indicates that RSU is offline and has not reported in some time.
- Heatmap: shows a heatmap based on message counts using the filters from the Message Count Menu
- V2x Message Viewer: query specific areas of the map to view corresponding messages or traffic data
- WZDx Viewer: display available WZDx work zone messages. Selecting a work zone icon will open a popup with details about the event
- Intersections: display locations of connected intersections and their corresponding intersection id
- Moove AI Viewer: query the map (by selecting a polygon region) for harsh braking events
- HAAS Alert Viewer: query HAAS alert incidents by time interval

The RSU Filters section allows users to narrow down the visible RSUs based on vendor and operational status.
This helps users focus on specific types of hardware or troubleshoot particular categories of RSUs.

The RSU Configuration section allows users to query for RSUs (by selecting a polygon on the map), and configure multiple RSUs at once.For more information on RSU configuration, see [Configuring RSUs](#configuring-rsus)

#### Configuring RSUs

Selecting an RSU on the map will open a popup window along with a side configuration menu. The popup displays detailed information including the RSU’s IP address, online status, last online time, milepost number, serial number, and the number of messages it has reported. The configuration menu allows users, depending on their access level, to perform actions such as retrieving the current message forwarding settings, modifying configurations, checking firmware updates, applying firmware updates, or rebooting the RSU.

<img src=../icons/rsu_popup_and_config_menu.png alt="RSU Popup and Configuration Panel"/>

**Note: To configure RSUs in bulk, see the Map Layers/RSU Configuration section**

##### Message Forwarding Current Configuration

The Current Configuration section shows the message forwarding rules applied to that RSU. These can come in 3 different types:

- TX: Forwarding rules specific to transmitted messages (TIM, MAP, SPAT, SSM)
- RX: Forwarding rules specific to received messages (BSM, SRM, SDSM)
- Generic (for devices running SNMP 4.1): Forwarding rules for all messages, transmitted or received

Forwarding rules have the following properties:

- Message Type: J2735 message type, rule only applies to that specific message
- Destination IP: Static IP address of the ODE UDP endpoint to forward messages to
- Port: UDP Port on the ODE (defaults to 46800)
- Start: Start date of forwarding rule
- End: End date of forwarding rule (defaults of 10 year lifespan)
- Security: Whether security headers are enabled
- Active: Whether the rule is currently enabled or disabled
- Delete Button: Select to delete that specific messaging rule

##### Message Forwarding Management

Create message forwarding rules. Enter the following information:

- Destination IP address: ODE static IP address (network accessible from RSU)
- Message Type
- Security Header (true/false)

##### Firmware Management

Check for available firmware upgrades and apply them, if available

##### Reboot

Reboot the selected RSU

### RSU Status and Message Counts

On the right side of the interface, there are two menu toggles that open menus with more detailed information about the RSUs. The first is the RSU Status Menu, which shows a full list of all RSUs and their current status. This menu can be toggled on or off by clicking the red X in the corner or by selecting the "Display RSU Status" toggle again. From this menu, users also have the option to print a complete report or an error-specific report for further analysis or record-keeping.

For each device listed, the following information is displayed:

- Location: Roadway and mile marker, select the "location" icon to center the map on the device
- Online Status: green indicates the RSU is online and actively reporting data, red indicates the RSU is offline, and yellow indicates recent status fluctuations are detected
- SCMS Status: Status of SCMS certificates, including the expiration date
- RSU IP Address

<img src=../icons/rsu_status_menu.png alt="RSU Status Menu Display" width="400"/>

The second menu is the Message Count Menu, which allows users to filter RSU message counts by selecting a
specific time range and message type. The top date-time picker sets the start of the range, while the bottom
one sets the end. Below these pickers, a dropdown allows users to choose the type of message they want to track.
Any change made to the date, time, or message type will automatically update the map and table view to reflect
the selected parameters.

<img src=../icons/rsu_count_menu.png alt="RSU Count Menu" width="300"/>

A table below the filter menu displays the number of messages received from each RSU within the specified time
range, regardless of whether the RSU is currently online. The table can be sorted by RSU name, road, or message
count by clicking on the appropriate column header. Each click toggles between ascending and descending order.

<img src=../icons/rsu_count.png alt="RSU Message Count Table" width="300"/>

The RSU Configuration section enables users to apply configuration changes to multiple RSUs within a selected
geographic area. This feature streamlines mass updates and management tasks across a network of RSUs.
