import EnvironmentVars from '../EnvironmentVars'

class AuthApi {
  async logIn(token) {
    const content = await fetch(EnvironmentVars.authEndpoint, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token,
      },
    })

    const json = await content.json()
    return {
      json: json,
      status: content.status
    }
  }
}

export default new AuthApi()
