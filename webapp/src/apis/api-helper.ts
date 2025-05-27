import toast from 'react-hot-toast'
import EnvironmentVars from '../EnvironmentVars'
import { evaluateFeatureFlags } from '../feature-flags'

class ApiHelper {
  formatQueryParams(query_params: Record<string, string>) {
    if (
      !query_params ||
      Object.keys(query_params).length === 0 ||
      Object.getPrototypeOf(query_params) !== Object.prototype
    )
      return ''
    const params = []
    for (const key in query_params) {
      if (query_params[key] !== '' && query_params[key] !== null) {
        params.push(`${key}=${query_params[key]}`)
      }
    }
    return !query_params || params.length === 0 ? '' : '?' + params.join('&')
  }

  async wrapResponseWithCode(
    response: Response,
    responseType: string,
    message: string | undefined = undefined
  ): Promise<{ status: number; body: any; message: string | undefined }> {
    return {
      status: response.status,
      body: responseType === 'blob' ? await response.blob() : await response.json(),
      message: message,
    }
  }

  async invokeApi({
    path,
    basePath,
    method = 'GET',
    headers = {},
    queryParams,
    body,
    token,
    timeout,
    abortController,
    responseType = 'json',
    booleanResponse = false,
    toastOnFailure = true,
    toastOnSuccess = false,
    successMessage = 'Successfully completed request!',
    failureMessage = 'Request failed to complete',
    tag,
    wrapResponseWithCode = false,
  }: {
    path: string
    basePath?: string
    method?: string
    headers?: Record<string, string>
    queryParams?: Record<string, string>
    body?: Object
    token?: string
    timeout?: number
    abortController?: AbortController
    responseType?: string
    booleanResponse?: boolean
    toastOnFailure?: boolean
    toastOnSuccess?: boolean
    successMessage?: string
    failureMessage?: string
    tag?: FEATURE_KEY
    wrapResponseWithCode?: boolean
  }): Promise<any> {
    if (!evaluateFeatureFlags(tag)) {
      console.debug(`Returning null because feature is disabled for tag ${tag} and path ${path}`)
      return null
    }
    const url = (basePath ?? EnvironmentVars.INTERSECTION_API_SERVER_URL!) + path + this.formatQueryParams(queryParams)

    const localHeaders: HeadersInit = { ...headers }
    if (token && basePath == EnvironmentVars.cvmanagerBaseEndpoint)
      localHeaders['Authorization'] = `${token}` // pass token directly to Authorization header for cvmanager API
    else if (token) localHeaders['Authorization'] = `Bearer ${token}`
    if (body && !('Content-Type' in localHeaders)) {
      localHeaders['Content-Type'] = 'application/json'
    }

    let id: NodeJS.Timeout | undefined = undefined
    if (timeout) {
      if (!abortController) {
        abortController = new AbortController()
      }
      id = setTimeout(() => abortController?.abort(), timeout)
    }

    const options: RequestInit = {
      method: method,
      headers: localHeaders,
      body: body
        ? localHeaders['Content-Type'] === 'application/x-www-form-urlencoded'
          ? (body as string)
          : JSON.stringify(body)
        : undefined,
      mode: 'cors',
      signal: abortController?.signal,
    }

    const resp = await fetch(url, options)
      .then((response) => {
        if (response.ok) {
          if (toastOnSuccess) toast.success(successMessage)
          if (wrapResponseWithCode) {
            return this.wrapResponseWithCode(response, responseType)
          }
          if (booleanResponse) return true
        } else {
          console.error('Request failed with status code ' + response.status + ': ' + response.statusText)
          if (response.status === 401) {
            toast.error('Authentication failed, please sign in again')
            // signIn();
          } else if (response.status === 403) {
            toast.error('You are not authorized to perform this action.')
          } else if (toastOnFailure) toast.error(failureMessage + ', with status code ' + response.status)

          if (wrapResponseWithCode) {
            return this.wrapResponseWithCode(response, responseType, failureMessage)
          }
          if (booleanResponse) return false
          return null
        }
        if (responseType === 'blob') {
          if (wrapResponseWithCode) {
            return this.wrapResponseWithCode(response, responseType)
          }
          return response.blob()
        } else {
          const resp = response.json()
          resp.catch((err) => {
            if (err.name !== 'AbortError') {
              console.error(err)
            }
          })

          if (wrapResponseWithCode) {
            return this.wrapResponseWithCode(response, responseType)
          }
          return resp
        }
      })
      .catch((error: Error) => {
        if (error.name !== 'AbortError') {
          const errorMessage = failureMessage ?? 'Fetch request failed'
          toast.error(errorMessage + '. Error: ' + error.message)
          console.error(error.message)
        }
      })
    if (id) clearTimeout(id)
    if (!resp) {
      if (wrapResponseWithCode) {
        return { status: undefined, body: null, message: undefined }
      } else {
        return null
      }
    }
    return resp
  }
}

export const apiHelper = new ApiHelper()
