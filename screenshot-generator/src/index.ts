import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import crypto from 'node:crypto'
import axios from 'axios'
import dotenv from 'dotenv'
import { BrowserContext, chromium, Page } from 'playwright'

dotenv.config()

type DecoderMessageType = 'MAP' | 'SPAT'

type BsmFeatureCollection = {
  type: 'FeatureCollection'
  features: unknown[]
}

type ProcessedMap = {
  properties: {
    intersectionId: number
  }
}

type ProcessedSpat = {
  intersectionId: number
}

type DecoderResponse = {
  asn1Text: string
  decodeTime: number
  decodeErrors: string
  type: string
  processedMap?: ProcessedMap
  processedSpat?: ProcessedSpat
}

type ScreenshotBootstrap = {
  intersectionId: number
  sourceData: {
    map: ProcessedMap[]
    spat: ProcessedSpat[]
    bsm: BsmFeatureCollection
  }
  options: {
    requireSignalState: boolean
  }
}

type CliOptions = {
  webappUrl: string
  apiUrl: string
  apiToken?: string
  storageState: string
  mapPath: string
  spatPaths: string[]
  outputPath: string
  bsmJsonPath?: string
  timeoutMs: number
  headed: boolean
  debugDir: string
  keycloakTokenUrl?: string
  keycloakClientId?: string
  keycloakClientSecret?: string
  keycloakUsername?: string
  keycloakPassword?: string
  keycloakScope?: string
}

type KeycloakTokenResponse = {
  access_token?: string
}

const coerceDecoderResponse = (raw: unknown): DecoderResponse => {
  if (typeof raw === 'string') {
    return JSON.parse(raw) as DecoderResponse
  }
  return raw as DecoderResponse
}

const defaultBsmCollection = (): BsmFeatureCollection => ({
  type: 'FeatureCollection',
  features: [],
})

const readTextFile = (filePath: string) => fs.readFileSync(path.resolve(filePath), 'utf8').trim()

const normalizeAsn1 = (contents: string) => contents.replace(/\s+/g, '')

const decodeAsn1 = async <T>(
  apiUrl: string,
  apiToken: string,
  type: DecoderMessageType,
  asn1Message: string
): Promise<T> => {
  const response = await axios.post<DecoderResponse | string>(
    new URL('/asn1/decoder/raw', ensureTrailingSlash(apiUrl)).toString(),
    {
      asn1Message,
      type,
    },
    {
      headers: {
        Authorization: `Bearer ${apiToken}`,
        'Content-Type': 'application/json',
      },
    }
  )

  const decodedResponse = coerceDecoderResponse(response.data)

  if (decodedResponse.decodeErrors) {
    throw new Error(`${type} decode failed: ${decodedResponse.decodeErrors}`)
  }

  console.log(decodedResponse)

  console.log(`Decoded Message with TYPE: ${decodedResponse.type}`)

  const payload = type === 'MAP' ? decodedResponse.processedMap : decodedResponse.processedSpat
  if (!payload) {
    throw new Error(`${type} decode succeeded but returned no processed payload`)
  }

  return payload as T
}

const ensureTrailingSlash = (value: string) => (value.endsWith('/') ? value : `${value}/`)

const parseBoolean = (value: string | undefined, defaultValue = false): boolean => {
  if (!value) return defaultValue
  const normalized = value.trim().toLowerCase()
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on'
}

const loadOptionsFromEnv = (): CliOptions => {
  const spatPaths = (process.env.SCREENSHOT_SPAT_PATHS ?? process.env.CV_MANAGER_SPAT_PATHS ?? '')
    .split(',')
    .map((v) => v.trim())
    .filter((v) => v.length > 0)

  const options: CliOptions = {
    webappUrl: process.env.CV_MANAGER_WEBAPP_URL ?? '',
    apiUrl: process.env.CV_MANAGER_API_URL ?? '',
    apiToken: process.env.CV_MANAGER_API_TOKEN,
    storageState: process.env.CV_MANAGER_STORAGE_STATE ?? '',
    mapPath: process.env.SCREENSHOT_MAP_PATH ?? process.env.CV_MANAGER_MAP_PATH ?? '',
    spatPaths,
    outputPath: process.env.SCREENSHOT_OUTPUT_PATH ?? process.env.CV_MANAGER_OUTPUT_PATH ?? '',
    bsmJsonPath: process.env.SCREENSHOT_BSM_JSON_PATH ?? process.env.CV_MANAGER_BSM_JSON_PATH,
    timeoutMs: Number(process.env.SCREENSHOT_TIMEOUT_MS ?? 30000),
    headed: parseBoolean(process.env.SCREENSHOT_HEADED, true),
    debugDir: process.env.SCREENSHOT_DEBUG_DIR ?? './debug',
    keycloakTokenUrl:
      process.env.KEYCLOAK_TOKEN_URL ?? 'http://localhost:8084/realms/cvmanager/protocol/openid-connect/token',
    keycloakClientId: process.env.KEYCLOAK_CLIENT_ID,
    keycloakClientSecret: process.env.KEYCLOAK_CLIENT_SECRET,
    keycloakUsername: process.env.KEYCLOAK_USERNAME,
    keycloakPassword: process.env.KEYCLOAK_PASSWORD,
  }

  if (!options.webappUrl || !options.apiUrl || !options.storageState || !options.mapPath || !options.outputPath) {
    throw new Error(
      'Missing required .env values. Required: CV_MANAGER_WEBAPP_URL, CV_MANAGER_API_URL, CV_MANAGER_STORAGE_STATE, SCREENSHOT_MAP_PATH, SCREENSHOT_OUTPUT_PATH'
    )
  }

  const hasApiToken = Boolean(options.apiToken)
  const hasKeycloakClient = Boolean(options.keycloakTokenUrl && options.keycloakClientId)
  if (!hasApiToken && !hasKeycloakClient) {
    throw new Error(
      'Missing API auth in .env. Provide CV_MANAGER_API_TOKEN, or Keycloak settings KEYCLOAK_TOKEN_URL and KEYCLOAK_CLIENT_ID.'
    )
  }

  if (!hasApiToken && (!options.keycloakUsername || !options.keycloakPassword)) {
    throw new Error('KEYCLOAK_USERNAME or KEYCLOAK_PASSWORD is missing.')
  }

  return options
}

const requestKeycloakAccessToken = async (options: CliOptions): Promise<string> => {
  console.log('Requesting Keycloak Access Token')
  if (!options.keycloakTokenUrl || !options.keycloakClientId) {
    throw new Error('Missing Keycloak token URL/client ID configuration.')
  }

  const form = new URLSearchParams()
  form.set('grant_type', 'password')
  form.set('client_id', options.keycloakClientId)
  if (options.keycloakClientSecret) {
    form.set('client_secret', options.keycloakClientSecret)
  }
  if (options.keycloakScope) {
    form.set('scope', options.keycloakScope)
  }

  if (!options.keycloakUsername || !options.keycloakPassword) {
    throw new Error('Keycloak username/password are required for password grant.')
  }
  form.set('username', options.keycloakUsername)
  form.set('password', options.keycloakPassword)

  const response = await axios.post<KeycloakTokenResponse>(options.keycloakTokenUrl, form.toString(), {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  })

  const token = response.data.access_token
  if (!token) {
    throw new Error('Keycloak token response did not include access_token.')
  }
  return token
}

const resolveApiToken = async (options: CliOptions): Promise<string> => {
  if (options.apiToken) {
    return options.apiToken
  }

  const token = await requestKeycloakAccessToken(options)
  console.log('Obtained API token from Keycloak.')
  return token
}

const readOptionalBsmJson = (bsmJsonPath?: string): BsmFeatureCollection => {
  if (!bsmJsonPath) {
    return defaultBsmCollection()
  }

  const parsed = JSON.parse(readTextFile(bsmJsonPath)) as BsmFeatureCollection
  if (parsed.type !== 'FeatureCollection' || !Array.isArray(parsed.features)) {
    throw new Error('The provided --bsm-json file is not a valid FeatureCollection')
  }
  return parsed
}

const filterAlignedSpat = (mapIntersectionId: number, spats: ProcessedSpat[]): ProcessedSpat[] => {
  return spats.filter((spat) => {
    if (spat.intersectionId !== mapIntersectionId) {
      console.warn(
        `Skipping SPAT with intersectionId ${spat.intersectionId} because MAP intersectionId is ${mapIntersectionId}.`
      )
      return false
    }
    return true
  })
}

const decodeOptionalSpat = async (
  apiUrl: string,
  apiToken: string,
  spatPath: string
): Promise<ProcessedSpat | undefined> => {
  try {
    const fileContents = readTextFile(spatPath)
    return await decodeAsn1<ProcessedSpat>(apiUrl, apiToken, 'SPAT', normalizeAsn1(fileContents))
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    console.warn(`Skipping SPAT source "${spatPath}": ${message}`)
    return undefined
  }
}

const waitForReadySignal = async (page: Page, timeoutMs: number) => {
  await page.waitForFunction(
    () => {
      const status = document.body.dataset.cvManagerScreenshotStatus
      const text = document.body.innerText ?? ''
      const isNotFound = text.includes('404 - Page Not Found')
      return status === 'ready' || status === 'error' || isNotFound
    },
    undefined,
    { timeout: timeoutMs }
  )

  const isNotFound = await page.evaluate(() => {
    const text = document.body.innerText ?? ''
    return text.includes('404 - Page Not Found')
  })

  if (isNotFound) {
    throw new Error(
      'Screenshot route returned 404 in the running webapp. Ensure the deployed webapp includes /dashboard/intersectionMap/screenshot.'
    )
  }

  const status = await page.evaluate(() => document.body.dataset.cvManagerScreenshotStatus ?? 'unknown')
  if (status === 'error') {
    const errorMessage = await page.evaluate(() => document.body.dataset.cvManagerScreenshotError ?? 'Unknown error')
    throw new Error(`Screenshot page failed to bootstrap: ${errorMessage}`)
  }
}

const collectPageDiagnostics = async (page: Page, options: CliOptions, reason: string) => {
  const debugDir = path.resolve(options.debugDir)
  fs.mkdirSync(debugDir, { recursive: true })

  const stamp = new Date().toISOString().replace(/[:.]/g, '-')
  const prefix = `capture-${stamp}`
  const screenshotPath = path.join(debugDir, `${prefix}.png`)
  const htmlPath = path.join(debugDir, `${prefix}.html`)

  try {
    await page.screenshot({ path: screenshotPath, fullPage: true })
    fs.writeFileSync(htmlPath, await page.content(), 'utf8')

    const currentUrl = page.url()
    const title = await page.title()
    const status = await page.evaluate(() => document.body.dataset.cvManagerScreenshotStatus ?? 'unknown')
    const error = await page.evaluate(() => document.body.dataset.cvManagerScreenshotError ?? '')

    console.error(
      `[diagnostics] reason="${reason}" url="${currentUrl}" title="${title}" status="${status}" error="${error}"`
    )
    console.error(`[diagnostics] screenshot=${screenshotPath}`)
    console.error(`[diagnostics] html=${htmlPath}`)
  } catch (diagError) {
    const message = diagError instanceof Error ? diagError.message : String(diagError)
    console.error(`[diagnostics] Failed to collect debug artifacts: ${message}`)
  }
}

const isKeycloakAuthPage = async (page: Page): Promise<boolean> => {
  const currentUrl = page.url().toLowerCase()
  if (currentUrl.includes('/protocol/openid-connect/auth')) {
    return true
  }

  const pageTitle = (await page.title()).toLowerCase()
  return pageTitle.includes('sign in') && (currentUrl.includes('/realms/') || currentUrl.includes('keycloak'))
}

const loginToKeycloakIfNeeded = async (
  page: Page,
  options: CliOptions,
  screenshotUrl: URL,
  context: BrowserContext
) => {
  const onKeycloakPage = await isKeycloakAuthPage(page)
  if (!onKeycloakPage) {
    return
  }

  if (!options.keycloakUsername || !options.keycloakPassword) {
    throw new Error('Redirected to Keycloak login page but KEYCLOAK_USERNAME/KEYCLOAK_PASSWORD are not configured.')
  }

  console.log('Detected Keycloak login page. Attempting automated login...')

  const usernameField = page.locator('input#username, input[name="username"]').first()
  const passwordField = page.locator('input#password, input[name="password"]').first()
  const submitButton = page
    .locator('input#kc-login, button#kc-login, button[type="submit"], input[type="submit"]')
    .first()

  await usernameField.waitFor({ state: 'visible', timeout: options.timeoutMs })
  await passwordField.waitFor({ state: 'visible', timeout: options.timeoutMs })

  await usernameField.fill(options.keycloakUsername)
  await passwordField.fill(options.keycloakPassword)

  await Promise.all([page.waitForLoadState('networkidle', { timeout: options.timeoutMs }), submitButton.click()])

  const postLoginUrl = page.url()
  if (!postLoginUrl.includes('/dashboard/intersectionMap/screenshot')) {
    const title = await page.title()
    throw new Error(`Keycloak login did not return to screenshot route. Current URL: ${postLoginUrl}, title: ${title}`)
  }

  await context.storageState({ path: path.resolve(options.storageState) })
  console.log(`Saved refreshed browser auth state to ${path.resolve(options.storageState)}`)

  // Ensure we're on the exact target URL containing bootstrapKey.
  if (postLoginUrl !== screenshotUrl.toString()) {
    await page.goto(screenshotUrl.toString(), { waitUntil: 'networkidle', timeout: options.timeoutMs })
  }
}

const main = async () => {
  const options = loadOptionsFromEnv()
  const apiToken = await resolveApiToken(options)

  const decodedMap = await decodeAsn1<ProcessedMap>(
    options.apiUrl,
    apiToken,
    'MAP',
    normalizeAsn1(readTextFile(options.mapPath))
  )

  console.log('Decoded MAP')

  const decodedSpatResults = await Promise.all(
    options.spatPaths.map((spatPath) => decodeOptionalSpat(options.apiUrl, apiToken, spatPath))
  )

  const intersectionId = decodedMap.properties.intersectionId
  const decodedSpat = filterAlignedSpat(
    intersectionId,
    decodedSpatResults.filter((spat): spat is ProcessedSpat => Boolean(spat))
  )

  const bootstrapPayload: ScreenshotBootstrap = {
    intersectionId,
    sourceData: {
      map: [decodedMap],
      spat: decodedSpat,
      bsm: readOptionalBsmJson(options.bsmJsonPath),
    },
    options: {
      requireSignalState: decodedSpat.length > 0,
    },
  }

  fs.mkdirSync(path.dirname(path.resolve(options.outputPath)), { recursive: true })

  const browser = await chromium.launch({ headless: !options.headed })
  const context = await browser.newContext({
    storageState: path.resolve(options.storageState),
    viewport: { width: 1600, height: 1200 },
    deviceScaleFactor: 1,
  })

  try {
    const page = await context.newPage()

    page.on('console', (message) => {
      console.log(`[browser:${message.type()}] ${message.text()}`)
    })

    page.on('pageerror', (error) => {
      console.error(`[browser:pageerror] ${error.message}`)
    })

    page.on('requestfailed', (request) => {
      console.error(`[browser:requestfailed] ${request.method()} ${request.url()} :: ${request.failure()?.errorText}`)
    })

    page.on('response', (response) => {
      if (response.status() >= 400) {
        console.error(`[browser:response] ${response.status()} ${response.url()}`)
      }
    })

    const bootstrapKey = `cv-manager-screenshot:${crypto.randomUUID()}`
    const bootstrapValue = JSON.stringify(bootstrapPayload)

    await page.addInitScript(
      ({ key, value }) => {
        window.sessionStorage.setItem(key, value)
      },
      { key: bootstrapKey, value: bootstrapValue }
    )

    const screenshotUrl = new URL('/dashboard/intersectionMap/screenshot', ensureTrailingSlash(options.webappUrl))
    screenshotUrl.searchParams.set('bootstrapKey', bootstrapKey)

    await page.goto(screenshotUrl.toString(), { waitUntil: 'networkidle', timeout: options.timeoutMs })

    await loginToKeycloakIfNeeded(page, options, screenshotUrl, context)

    const currentUrl = page.url()
    console.log(`Navigated to: ${currentUrl}`)
    if (!currentUrl.includes('/dashboard/intersectionMap/screenshot')) {
      console.warn('Unexpected URL after navigation; possible auth redirect or routing issue.')
    }

    try {
      await waitForReadySignal(page, options.timeoutMs)
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      await collectPageDiagnostics(page, options, reason)
      throw error
    }

    const canvasLocator = page.locator('canvas.mapboxgl-canvas').first()
    await canvasLocator.waitFor({ state: 'visible', timeout: options.timeoutMs })
    await canvasLocator.screenshot({ path: path.resolve(options.outputPath) })

    console.log(
      JSON.stringify(
        {
          outputPath: path.resolve(options.outputPath),
          intersectionId,
          spatMessages: decodedSpat.length,
        },
        null,
        2
      )
    )
  } finally {
    await context.close()
    await browser.close()
  }
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error)
  console.error(message)
  process.exitCode = 1
})
