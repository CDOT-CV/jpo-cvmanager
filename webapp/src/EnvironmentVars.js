class EnvironmentVars {
  static getBaseApiUrl() {
    return process.env.REACT_APP_GATEWAY_BASE_URL
  }

  static MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_TOKEN
  static KEYCLOAK_HOST_URL = process.env.REACT_APP_KEYCLOAK_URL

  static rsuInfoEndpoint = `${this.getBaseApiUrl()}/rsuinfo`
  static rsuOnlineEndpoint = `${this.getBaseApiUrl()}/rsu-online-status`
  static rsuCountsEndpoint = `${this.getBaseApiUrl()}/rsucounts`
  static rsuCommandEndpoint = `${this.getBaseApiUrl()}/rsu-command`
  static wzdxEndpoint = `${this.getBaseApiUrl()}/wzdx-feed`
  static rsuMapInfoEndpoint = `${this.getBaseApiUrl()}/rsu-map-info`
  static rsuGeoQueryEndpoint = `${this.getBaseApiUrl()}/rsu-geo-query`
  // static bsmDataEndpoint = `${this.getBaseApiUrl()}/rsu-bsm-data`
  // static psmDataEndpoint = `${this.getBaseApiUrl()}/rsu-psm-data`
  static geoMsgDataEndpoint = `${this.getBaseApiUrl()}/rsu-geo-msg-data`
  static issScmsStatusEndpoint = `${this.getBaseApiUrl()}/iss-scms-status`
  static ssmSrmEndpoint = `${this.getBaseApiUrl()}/rsu-ssm-srm-data`
  static authEndpoint = `${this.getBaseApiUrl()}/user-auth`
  static adminAddRsu = `${this.getBaseApiUrl()}/admin-new-rsu`
  static adminRsu = `${this.getBaseApiUrl()}/admin-rsu`
  static adminAddUser = `${this.getBaseApiUrl()}/admin-new-user`
  static adminUser = `${this.getBaseApiUrl()}/admin-user`
  static adminAddOrg = `${this.getBaseApiUrl()}/admin-new-org`
  static adminOrg = `${this.getBaseApiUrl()}/admin-org`
  static contactSupport = `${this.getBaseApiUrl()}/contact-support`
}

export default EnvironmentVars
