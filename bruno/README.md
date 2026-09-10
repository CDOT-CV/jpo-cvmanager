# Bruno API Collection — Intersection API

[Bruno](https://www.usebruno.com/) is an open-source desktop API client that stores collections as plain files directly in the repository. This collection covers all Intersection API endpoints and handles authentication automatically.

## Installation

1. Download and install Bruno from [https://www.usebruno.com/downloads](https://www.usebruno.com/downloads)
2. Open Bruno
3. Click **Open Collection** and select the `bruno/Intersection API` folder in this repository

## Required Services

Before making requests, ensure the following services are running (via Docker Compose or locally):

| Service              | Default Port | Purpose                       |
| -------------------- | ------------ | ----------------------------- |
| `cvmanager_postgres` | 5432         | Application database          |
| `cvmanager_keycloak` | 8084         | Authentication / token issuer |
| `intersection_api`   | 8089         | The API being tested          |

To start these with Docker Compose:

```sh
docker compose up -d cvmanager_postgres cvmanager_keycloak intersection_api
```

## Setting Your Environment

This collection includes a **Localhost** environment with pre-configured values for local development.

1. In Bruno, click the environment dropdown in the top-right corner of the collection
2. Select **Localhost**

The Localhost environment is pre-configured with:

| Variable       | Default Value           |
| -------------- | ----------------------- |
| `baseUrl`      | `http://localhost:8089` |
| `kc-endpoint`  | `http://localhost:8084` |
| `kc-realm`     | `cvmanager`             |
| `kc-client-id` | `cvmanager-gui`         |
| `kc-username`  | `test@gmail.com`        |
| `kc-password`  | `tester`                |

To override values (e.g. a different user or remote host), click the **Localhost** environment and edit the variables directly. These changes are local and not committed to source control.

## Authentication

Authentication is handled automatically. The collection uses OAuth2 Resource Owner Password flow — Bruno will fetch and attach a Bearer token before each request using the Keycloak credentials from your active environment. No manual token management is needed.

If a token expires, Bruno will automatically refresh it (`autoRefreshToken` is enabled).

## Making Requests

1. Expand the collection folders (e.g. `user-controller`, `organization-controller`) to see available requests
2. Click any request and hit **Send**
3. Bruno will fetch a token from Keycloak, include it in the `Authorization` header, and send the request to the Intersection API
