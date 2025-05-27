class EnvironmentVars {
  static getBaseApiUrl() {
    return process.env.REACT_APP_GATEWAY_BASE_URL
  }

  static getMessageTypes() {
    const COUNT_MESSAGE_TYPES = process.env.REACT_APP_COUNT_MESSAGE_TYPES
    if (!COUNT_MESSAGE_TYPES) {
      return []
    }
    const messageTypes = COUNT_MESSAGE_TYPES.split(',').map((item) => item.trim())
    return messageTypes
  }

  static getMessageViewerTypes() {
    const VIEWER_MESSAGE_TYPES = process.env.REACT_APP_VIEWER_MESSAGE_TYPES
    if (!VIEWER_MESSAGE_TYPES) {
      return ['BSM'] // default to BSM if not set
    }
    const messageTypes = VIEWER_MESSAGE_TYPES.split(',').map((item) => item.trim())
    return messageTypes
  }

  static getMapboxInitViewState() {
    const MAPBOX_INIT_LATITUDE = Number(process.env.REACT_APP_MAPBOX_INIT_LATITUDE)
    const MAPBOX_INIT_LONGITUDE = Number(process.env.REACT_APP_MAPBOX_INIT_LONGITUDE)
    const MAPBOX_INIT_ZOOM = Number(process.env.REACT_APP_MAPBOX_INIT_ZOOM)

    const viewState = {
      latitude: MAPBOX_INIT_LATITUDE,
      longitude: MAPBOX_INIT_LONGITUDE,
      zoom: MAPBOX_INIT_ZOOM,
    }

    return viewState
  }

  static MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_TOKEN
  static INTERSECTION_API_SERVER_URL = process.env.REACT_APP_CVIZ_API_SERVER_URL
  static INTERSECTION_API_WS_URL = process.env.REACT_APP_CVIZ_API_WS_URL
  static KEYCLOAK_HOST_URL = process.env.REACT_APP_KEYCLOAK_URL
  static KEYCLOAK_REALM = process.env.REACT_APP_KEYCLOAK_REALM
  static KEYCLOAK_CLIENT_ID = process.env.REACT_APP_KEYCLOAK_CLIENT_ID
  static DOT_NAME = process.env.REACT_APP_DOT_NAME
  static ENABLE_RSU_FEATURES = process.env.REACT_APP_ENABLE_RSU_FEATURES !== 'false'
  static ENABLE_INTERSECTION_FEATURES = process.env.REACT_APP_ENABLE_INTERSECTION_FEATURES !== 'false'
  static ENABLE_WZDX_FEATURES = process.env.REACT_APP_ENABLE_WZDX_FEATURES !== 'false'
  static ENABLE_MOOVE_AI_FEATURES = process.env.REACT_APP_ENABLE_MOOVE_AI_FEATURES !== 'false'
  static WEBAPP_THEME_LIGHT = process.env.REACT_APP_WEBAPP_THEME_LIGHT
  static WEBAPP_THEME_DARK = process.env.REACT_APP_WEBAPP_THEME_DARK

  static cvmanagerBaseEndpoint = `${this.getBaseApiUrl()}`
  static rsuInfoEndpoint = `/rsuinfo`
  static rsuOnlineEndpoint = `/rsu-online-status`
  static rsuCountsEndpoint = `/rsucounts`
  static rsuCommandEndpoint = `/rsu-command`
  static wzdxEndpoint = `/wzdx-feed`
  static rsuGeoQueryEndpoint = `/rsu-geo-query`
  static rsuMsgFwdQueryEndpoint = `/rsu-msgfwd-query`
  static geoMsgDataEndpoint = `/rsu-geo-msg-data`
  static mooveAiDataEndpoint = `/moove-ai-data`
  static issScmsStatusEndpoint = `/iss-scms-status`
  static ssmSrmEndpoint = `/rsu-ssm-srm-data`
  static authEndpoint = `/user-auth`
  static adminAddRsu = `/admin-new-rsu`
  static adminRsu = `/admin-rsu`
  static adminAddIntersection = `/admin-new-intersection`
  static adminIntersection = `/admin-intersection`
  static adminAddUser = `/admin-new-user`
  static adminUser = `/admin-user`
  static adminNotification = `/admin-notification`
  static adminAddNotification = `/admin-new-notification`
  static adminAddOrg = `/admin-new-org`
  static adminOrg = `/admin-org`
  static contactSupport = `/contact-support`
  static rsuErrorSummary = `/rsu-error-summary`
}

export default EnvironmentVars
