# jpo-cvmanager

**US Department of Transportation (USDOT) Intelligent Transportation Systems (ITS) Joint Program Office (JPO) Connected Vehicle Manager**

The JPO Connected Vehicle Manager is a web-based application that helps an organization manage their deployed CV devices (Roadside Units and Onboard Units) through an interactive, graphical user interface using Mapbox.

<b>GUI:</b> ReactJS with Redux Toolkit and Mapbox GL

<b>API:</b> Python

<b>Features:</b>

- Visualize devices on a Mapbox map
- Display the current statuses of devices
  - Latest online status
  - ISS SCMS certificate expiration
  - Other identifying values tracked on a PostgreSQL database
- jpo-ode supported message counts, sorted by RSU IP (BSM, MAP, SPaT, SRM, SSM, TIM)
- Visualize an RSU's currently active MAP message
- Visualize Basic Safety Messages (BSMs) relative to a specified geofence and time period
- Device configuration over SNMP (v3) for message forwarding
- Device firmware upgrade support for Kapsch, Commsignia and Yunex devices
- Admin controls for adding, modifying and removing devices and users

To provide feedback, we recommend that you create an "issue" in this repository (<https://github.com/usdot-jpo-ode/jpo-cvmanager/issues>). You will need a GitHub account to create an issue. If you don’t have an account, a dialog will be presented to you to create one at no cost.

## Release Notes

The current version and release history of the JPO CV Manager: [Release Notes](docs/Release_notes.md)

## Requirements and Limitations

The JPO CV Manager was originally developed for the Google Cloud Platform and a few of its GCP dependencies still remain. The GCP dependencies will eventually be streamlined to support other options. However, there are a handful of technologies to know before attempting to utilize the CV Manager.

### CV Manager Webapp

- Supports OAuth2.0 through Keycloak for user authentication only. It can be configured for several different Identity Providers, however it has only been tested with Google Oath.

### CV Manager API

- PostgreSQL database is required. Run the [table creation script to create a to-spec database](resources/sql_scripts).
  - Follow along with the README to ensure your data is properly populated before running the CV Manager.
- GCP BigQuery is required to support J2735 message counts and BSM data. Message counts will be migrated to PostgreSQL eventually, however it is not recommended to store full J2735 messages in a PostgreSQL database. A noSQL database or a database that is specialized for storing big data is recommended. Support for MongoDB is planned to be implemented.
  - It is recommended to create a table for storing J2735 messages, one table per message type (BSM, MAP, SPaT, SRM, and SSM), before running the CV Manager.

### Keycloak

- Keycloak is used for the CV Manager Webapp's Authentication.
- The Keycloak pod requires a `realm.json` file in the folder: `./resources/keycloak/` to startup with the proper configurations. It also requires a login theme that can be modified and generated using the [keycloakify](https://github.com/keycloakify/keycloakify) forked repository in resources/keycloak/keycloakify.

## Getting Started

The following steps are intended to help get a new user up and running the JPO CV Manager in their own environment.

1.  Follow the Requirements and Limitations section and make sure all requirements are met.
2.  The CV Manager has four components that need to be containerized and deployed: the API, the PostgreSQL database, Keycloak, and the webapp.

    - If you are looking to deploy the CV Manager locally, you can simply run the docker-compose, make sure to fill out the .env file to ensure it launches properly. Also, edit your host file ([How to edit the host file](<[resources/kubernetes](https://docs.rackspace.com/support/how-to/modify-your-hosts-file/)>)) and add the following config where `8.8.8.8` should be replaced with the IP address of your WSL:

    CV Manager hosts:

         8.8.8.8 cvmanager.local.com
         8.8.8.8 cvmanager.auth.com

3.  A login theme must be generated for Keycloak by running the following commands:

         yarn --cwd resources\keycloak\keycloakify
         yarn --cwd resources\keycloak\keycloakify build-keycloak-theme

- If you are looking to deploy in Kubernetes or on separate VMs, refer to the Kubernetes YAML deployment files to deploy the four components to your cluster. ([Kubernetes YAML](resources/kubernetes))

1. The API is available on port 8080. The webapp is available on port 80.

## License Information

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
file except in compliance with the License.
You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>
Unless required by applicable law or agreed to in writing, software distributed under
the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied. See the License for the specific language governing
permissions and limitations under the [License](http://www.apache.org/licenses/LICENSE-2.0).
