import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import RsuApi from '../apis/rsu-api'
import RsuFirmwareApi from '../apis/intersections/rsu-firmware-api'
import RsuMessageForwardingApi from '../apis/intersections/rsu-message-forwarding-api'
import {
  ForwardingMessageType,
  MessageForwardingResponse,
  MessageForwardingResult,
} from '../models/RsuMessageForwarding'
import { selectToken, selectOrganizationName } from './userSlice'
import { RootState } from '../store'
import {
  ApiMsgRespWithCodes,
  RsuCommandPostBody,
  RsuDsrcFwdConfigs,
  RsuRxTxMsgFwdConfigs,
  RsuUpgradeCheckPostBody,
  RsuUpgradePostBody,
} from '../models/RsuApi'

const initialState = {
  msgFwdConfig: {} as any,
  errorState: '',
  changeSuccess: false,
  rebootChangeSuccess: false,
  firmwareUpgradeAvailable: false,
  firmwareUpgradeName: '',
  firmwareUpgradeMsg: '',
  firmwareUpgradeErr: false,
  destIp: '',
  snmpMsgType: 'bsm',
  includeSecurityHeader: false,
  addConfigPoint: false,
  configCoordinates: [] as number[][],
  configList: [] as string[],
  msgFwdConfigType: 'database' as 'database' | 'rsu',
  // Shared by cache and live reads so late replies cannot replace a newer selection.
  forwardingReadRequestId: undefined as string | undefined,
}

export const getCachedSnmpFwdConfigsFromDatabase = createAsyncThunk<
  { msgFwdConfig: RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs; errorState: string },
  string,
  { state: RootState }
>('config/refreshSnmpFwdConfig', async (rsu_ip: string, { getState, rejectWithValue }) => {
  const currentState = getState() as RootState
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)

  try {
    const response = await RsuMessageForwardingApi.cached(token, organization, rsu_ip)
    if (!response || response.status !== 200) {
      return rejectWithValue(response?.message || 'Failed to retrieve cached RSU message forwarding configuration')
    }
    return { msgFwdConfig: response.body.RsuFwdSnmpwalk, errorState: '' }
  } catch (error) {
    return rejectWithValue(
      error instanceof Error ? error.message : 'Failed to retrieve cached RSU message forwarding configuration'
    )
  }
})

export const getRsuMsgConfigsFromRsu = createAsyncThunk<
  { msgFwdConfig: RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs; errorState: string },
  string,
  { state: RootState }
>('config/getRsuMsgFwdFetch', async (rsu_ip: string, { getState, rejectWithValue }) => {
  const currentState = getState() as RootState
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)

  try {
    const response = await RsuMessageForwardingApi.live(token, organization, rsu_ip)
    if (!response || response.status !== 200) {
      return rejectWithValue(response?.message || 'Failed to fetch RSU message forwarding configuration')
    }
    return {
      msgFwdConfig: response.body.RsuFwdSnmpwalk,
      errorState: '',
    }
  } catch (e) {
    if (e instanceof Error) {
      return rejectWithValue(e.message)
    }
    if (typeof e === 'string') {
      return rejectWithValue(e)
    }
    return rejectWithValue('An unknown error occurred while fetching RSU message forwarding configuration')
  }
})

// The API limits each batch to 100 targets. Send larger UI selections in sequence,
// without replaying any request, and inspect every expected device result even after HTTP 200.
async function executeForwardingRequests(
  ips: string[],
  operation: 'create' | 'delete',
  send: (batch: string[]) => Promise<ApiMsgRespWithCodes<MessageForwardingResponse> | null>
) {
  const selected = [...new Set(ips)]
  if (selected.length === 0) return { changeSuccess: false, errorState: 'No RSUs selected' }
  const failures: string[] = []
  for (let offset = 0; offset < selected.length; offset += 100) {
    const batch = selected.slice(offset, offset + 100)
    let response: ApiMsgRespWithCodes<MessageForwardingResponse> | null
    try {
      response = await send(batch)
    } catch {
      response = null
    }
    for (const ip of batch) {
      if (!response || response.status !== 200) {
        failures.push(`${ip}: ${response?.message || 'Request did not complete; check RSU state before retrying.'}`)
        continue
      }
      const result = response.body?.[ip]
      const confirmed = result?.writeState === 'VERIFIED' && result.changed === true
      const noOp = operation === 'delete' && result?.writeState === 'NOT_SUBMITTED' && result.changed === false
      if (result?.execution?.status === 'SUCCEEDED' && result.databaseSync === 'UPDATED' && (confirmed || noOp))
        continue
      failures.push(`${ip}: ${forwardingFailureMessage(result)}`)
    }
  }
  return {
    changeSuccess: failures.length === 0,
    errorState:
      failures.length === 0
        ? ''
        : `${selected.length - failures.length} of ${selected.length} RSUs completed successfully. ${failures.join('; ')}`,
  }
}

function forwardingFailureMessage(result: MessageForwardingResult | undefined): string {
  if (!result) return 'No result returned; check RSU state before retrying.'
  if (result.writeState === 'UNKNOWN') return 'Write not confirmed; check RSU state before retrying.'
  if (result.writeState === 'VERIFIED' && result.databaseSync !== 'UPDATED') {
    return result.databaseSync === 'FAILED'
      ? 'Forwarding change verified, but the database update failed. Check RSU state before retrying.'
      : 'Forwarding change verified, but the database was not synchronized. Check RSU state before retrying.'
  }
  return result.execution?.message || 'Forwarding change could not be confirmed; check RSU state before retrying.'
}

function forwardingMessageType(value: string): ForwardingMessageType {
  const normalized = value.toUpperCase()
  if (!['BSM', 'SPAT', 'MAP', 'SSM', 'SRM', 'TIM'].includes(normalized)) {
    throw new Error(`Unsupported forwarding message type: ${value}`)
  }
  return normalized as ForwardingMessageType
}

export const submitSnmpSet = createAsyncThunk('config/submitSnmpSet', async (ipList: string[], { getState }) => {
  const currentState = getState() as RootState
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  const messageType = forwardingMessageType(selectSnmpMsgType(currentState))
  const destination = selectDestIp(currentState)
  const includeSecurity = selectIncludeSecurityHeader(currentState)
  return executeForwardingRequests(ipList, 'create', (batch) =>
    RsuMessageForwardingApi.create(token, organization, {
      rsu_ips: batch,
      message_type: messageType,
      destination_ip: destination,
      include_security: includeSecurity,
    })
  )
})

export const deleteSnmpSet = createAsyncThunk(
  'config/deleteSnmpSet',
  async (data: { ipList: string[]; destIp: string; snmpMsgType: string }, { getState }) => {
    const currentState = getState() as RootState
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)
    const messageType = forwardingMessageType(data.snmpMsgType)
    return executeForwardingRequests(data.ipList, 'delete', (batch) =>
      RsuMessageForwardingApi.delete(token, organization, {
        rsu_ips: batch,
        message_type: messageType,
        destination_ip: data.destIp,
      })
    )
  }
)

export const rebootRsu = createAsyncThunk('config/rebootRsu', async (ipList: string[], { getState }) => {
  const currentState = getState() as RootState
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)

  const body: RsuCommandPostBody = {
    command: 'reboot',
    rsu_ip: ipList,
    args: {},
  }

  RsuApi.postRsuData(token, organization, body, '')

  return
})

export const checkFirmwareUpgrade = createAsyncThunk(
  'config/checkFirmwareUpgrade',
  async (rsuIps: string[], { getState, rejectWithValue }) => {
    if (rsuIps.length !== 1) {
      return rejectWithValue('Firmware upgrade availability check requires exactly one RSU')
    }

    const currentState = getState() as RootState
    const token = selectToken(currentState)

    const body: RsuUpgradeCheckPostBody = {
      rsu_ip: rsuIps[0],
      args: {},
    }

    const response = await RsuFirmwareApi.postRsuUpgradeData(token, body, '/check')
    if (!response) {
      return rejectWithValue('Failed to check firmware upgrade availability')
    }
    if (response.status >= 400) {
      return rejectWithValue(response.message || 'Failed to check firmware upgrade availability')
    }
    return {
      firmwareUpgradeAvailable: response.body?.upgrade_available,
      firmwareUpgradeName: response.body?.upgrade_name,
    }
  }
)

export const startFirmwareUpgrade = createAsyncThunk(
  'config/startFirmwareUpgrade',
  async (rsuIps: string[], { getState, rejectWithValue }) => {
    const currentState = getState() as RootState
    const token = selectToken(currentState)

    const body: RsuUpgradePostBody = {
      rsu_ips: rsuIps,
      args: {},
    }

    const response = await RsuFirmwareApi.postRsuUpgradeData(token, body, '')
    if (!response) {
      return rejectWithValue('Firmware upgrades failed to be started')
    }
    if (response.status >= 400) {
      return rejectWithValue(response.message || 'Firmware upgrades failed to be started')
    }

    const perRsuResults = response.body as Record<string, { code?: number; data?: unknown }> | undefined
    if (!perRsuResults || typeof perRsuResults !== 'object') {
      return response.status === 200
        ? { message: 'Firmware upgrades started successfully', statusCode: response.status }
        : { message: 'Firmware upgrades failed to be started', statusCode: response.status }
    }

    const failedIps: string[] = []
    const startedIps: string[] = []
    const upToDateIps: string[] = []

    const extractDataMessage = (data: unknown): string => {
      if (typeof data === 'string') {
        return data
      }
      if (data && typeof data === 'object' && 'message' in data) {
        const message = (data as { message?: unknown }).message
        return typeof message === 'string' ? message : ''
      }
      return ''
    }

    rsuIps.forEach((ip) => {
      const code = perRsuResults?.[ip]?.code
      const dataMessage = extractDataMessage(perRsuResults?.[ip]?.data)
      const isUpToDate = code === 409 && dataMessage.toLowerCase().includes('already up to date')

      if (code === 200 || code === 201) {
        startedIps.push(ip)
      } else if (isUpToDate) {
        upToDateIps.push(ip)
      } else {
        failedIps.push(ip)
      }
    })

    if (rsuIps.length === 1) {
      if (upToDateIps.length > 0) {
        return {
          message: 'Selected RSU is already up to date.',
          statusCode: 200,
        }
      }

      return failedIps.length > 0
        ? { message: 'Firmware upgrade failed to start.', statusCode: perRsuResults?.[rsuIps[0]]?.code ?? 500 }
        : { message: 'Firmware upgrade started successfully.', statusCode: 200 }
    }

    if (failedIps.length === 0) {
      if (upToDateIps.length > 0 && startedIps.length > 0) {
        return {
          message: `Firmware upgrade started for ${startedIps.length} RSUs. ${upToDateIps.length} RSUs already up to date.`,
          statusCode: 200,
        }
      }
      if (upToDateIps.length > 0) {
        return {
          message: 'Selected RSUs are already up to date.',
          statusCode: 200,
        }
      }
      return { message: `Firmware upgrade started for ${startedIps.length} RSUs.`, statusCode: 200 }
    }

    if (startedIps.length === 0) {
      return {
        message: `Firmware upgrade failed for selected RSUs: ${failedIps.join(', ')}`,
        statusCode: 500,
      }
    }

    const successSummary = startedIps.length > 0 ? `Firmware upgrade started for ${startedIps.length} RSUs.` : ''
    const upToDateSummary = upToDateIps.length > 0 ? ` ${upToDateIps.length} RSUs already up to date.` : ''
    const firstFailedIpCode = failedIps.length > 0 ? (perRsuResults?.[failedIps[0]]?.code ?? 500) : 500

    return {
      message: `${successSummary}${upToDateSummary} Failed: ${failedIps.join(', ')}`.trim(),
      statusCode: firstFailedIpCode,
    }
  }
)

export const geoRsuQuery = createAsyncThunk(
  'config/geoRsuQuery',
  async (vendor: string, { getState, rejectWithValue }) => {
    const currentState = getState() as RootState
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)
    const configCoordinates = selectConfigCoordinates(currentState)

    const response = await RsuApi.postRsuGeo(token, organization, {
      geometry: configCoordinates,
      vendor,
    })
    if (!response || response.status !== 200) {
      return rejectWithValue(response?.message || 'Failed to query RSUs by geometry')
    }
    return response
  },
  {
    // Will guard thunk from being executed
    condition: (_, { getState }) => {
      const currentState = getState() as RootState as RootState
      const configCoordinates = selectConfigCoordinates(currentState)

      const valid = configCoordinates.length > 2
      return valid
    },
  }
)

export const configSlice = createSlice({
  name: 'config',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {
    setDestIp: (state, action) => {
      state.value.destIp = action.payload
    },
    setMsgType: (state, action) => {
      state.value.snmpMsgType = action.payload
    },
    setIncludeSecurityHeader: (state, action) => {
      state.value.includeSecurityHeader = action.payload
    },
    toggleConfigPointSelect: (state) => {
      state.value.addConfigPoint = !state.value.addConfigPoint
    },
    updateConfigPoints: (state, action) => {
      state.value.configCoordinates = action.payload
    },
    clearConfig: (state) => {
      state.value.configCoordinates = []
      state.value.configList = []
      state.loading = false
    },
    clearFirmware: (state) => {
      state.value.firmwareUpgradeAvailable = false
      state.value.firmwareUpgradeName = ''
      state.value.firmwareUpgradeMsg = ''
      state.value.firmwareUpgradeErr = true
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getCachedSnmpFwdConfigsFromDatabase.pending, (state, action) => {
        state.value.forwardingReadRequestId = action.meta.requestId
        state.loading = true
        state.value.msgFwdConfig = {} as RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs
        state.value.errorState = ''
        state.value.destIp = ''
        state.value.snmpMsgType = 'bsm'
        state.value.changeSuccess = false
        state.value.rebootChangeSuccess = false
      })
      .addCase(getCachedSnmpFwdConfigsFromDatabase.fulfilled, (state, action) => {
        if (state.value.forwardingReadRequestId !== action.meta.requestId) return
        state.loading = false
        state.value.msgFwdConfig = action.payload.msgFwdConfig ?? ({} as RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs)
        state.value.errorState = action.payload.errorState
        state.value.msgFwdConfigType = 'database'
      })
      .addCase(getCachedSnmpFwdConfigsFromDatabase.rejected, (state, action) => {
        if (state.value.forwardingReadRequestId !== action.meta.requestId) return
        state.loading = false
        state.value.errorState =
          (action.payload as string | undefined) || 'Failed to retrieve cached RSU message forwarding configuration'
        state.value.msgFwdConfigType = 'database'
      })
      .addCase(getRsuMsgConfigsFromRsu.pending, (state, action) => {
        state.value.forwardingReadRequestId = action.meta.requestId
        state.loading = true
        state.value.msgFwdConfig = {} as RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs
        state.value.errorState = ''
        state.value.destIp = ''
        state.value.snmpMsgType = 'bsm'
        state.value.changeSuccess = false
        state.value.rebootChangeSuccess = false
      })
      .addCase(getRsuMsgConfigsFromRsu.fulfilled, (state, action) => {
        if (state.value.forwardingReadRequestId !== action.meta.requestId) return
        state.loading = false
        state.value.msgFwdConfig = action.payload.msgFwdConfig ?? ({} as RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs)
        state.value.errorState = action.payload.errorState
        state.value.msgFwdConfigType = 'rsu'
      })
      .addCase(getRsuMsgConfigsFromRsu.rejected, (state, action) => {
        if (state.value.forwardingReadRequestId !== action.meta.requestId) return
        state.loading = false
        state.value.errorState =
          (action.payload as string | undefined) || 'Failed to fetch RSU message forwarding configuration'
      })
      .addCase(submitSnmpSet.pending, (state) => {
        state.loading = true
        state.value.changeSuccess = false
        state.value.errorState = ''
      })
      .addCase(submitSnmpSet.fulfilled, (state, action) => {
        state.loading = false
        state.value.changeSuccess = action.payload.changeSuccess
        state.value.errorState = action.payload.errorState
      })
      .addCase(submitSnmpSet.rejected, (state, action) => {
        state.loading = false
        state.value.changeSuccess = false
        state.value.errorState = action.error.message || 'Failed to add SNMP configuration'
      })
      .addCase(deleteSnmpSet.pending, (state) => {
        state.loading = true
        state.value.changeSuccess = false
        state.value.errorState = ''
      })
      .addCase(deleteSnmpSet.fulfilled, (state, action) => {
        state.loading = false
        state.value.changeSuccess = action.payload.changeSuccess
        state.value.errorState = action.payload.errorState
      })
      .addCase(deleteSnmpSet.rejected, (state, action) => {
        state.loading = false
        state.value.changeSuccess = false
        state.value.errorState = action.error.message || 'Failed to delete SNMP configuration'
      })
      .addCase(rebootRsu.pending, (state) => {
        state.loading = true
        state.value.rebootChangeSuccess = false
      })
      .addCase(rebootRsu.fulfilled, (state) => {
        state.loading = false
        state.value.rebootChangeSuccess = true
      })
      .addCase(rebootRsu.rejected, (state) => {
        state.loading = false
        state.value.rebootChangeSuccess = false
      })
      .addCase(checkFirmwareUpgrade.pending, (state) => {
        state.loading = true
        state.value.firmwareUpgradeAvailable = false
        state.value.firmwareUpgradeName = ''
        state.value.firmwareUpgradeErr = false
      })
      .addCase(checkFirmwareUpgrade.fulfilled, (state, action) => {
        state.loading = false
        state.value.firmwareUpgradeAvailable = action.payload.firmwareUpgradeAvailable
        state.value.firmwareUpgradeName = action.payload.firmwareUpgradeName
        if (!action.payload.firmwareUpgradeAvailable) state.value.firmwareUpgradeMsg = 'Firmware is up to date!'
      })
      .addCase(checkFirmwareUpgrade.rejected, (state, action) => {
        state.loading = false
        state.value.firmwareUpgradeAvailable = false
        state.value.firmwareUpgradeName = ''
        state.value.firmwareUpgradeMsg =
          (action.payload as string | undefined) || 'An error occurred while checking for an upgrade'
        state.value.firmwareUpgradeErr = true
      })
      .addCase(startFirmwareUpgrade.pending, (state) => {
        state.loading = true
        state.value.firmwareUpgradeErr = false
      })
      .addCase(startFirmwareUpgrade.fulfilled, (state, action) => {
        state.loading = false
        state.value.firmwareUpgradeAvailable = false
        state.value.firmwareUpgradeName = ''
        state.value.firmwareUpgradeMsg = action.payload.message
        if (action.payload.statusCode !== 201 && action.payload.statusCode !== 200)
          state.value.firmwareUpgradeErr = true
      })
      .addCase(startFirmwareUpgrade.rejected, (state, action) => {
        state.loading = false
        state.value.firmwareUpgradeAvailable = false
        state.value.firmwareUpgradeName = ''
        state.value.firmwareUpgradeMsg =
          (action.payload as string | undefined) || 'An error occurred while starting the firmware upgrade'
        state.value.firmwareUpgradeErr = true
      })
      .addCase(geoRsuQuery.pending, (state) => {
        state.loading = true
        state.value.errorState = ''
        state.value.destIp = ''
        state.value.snmpMsgType = 'bsm'
        state.value.changeSuccess = false
        state.value.rebootChangeSuccess = false
      })
      .addCase(geoRsuQuery.fulfilled, (state, action) => {
        state.value.configList = action.payload.body ?? []
        state.value.addConfigPoint = false
        state.loading = false
      })
      .addCase(geoRsuQuery.rejected, (state) => {
        state.loading = false
      })
  },
})

export const selectMsgFwdConfig = (state: RootState) => state.config.value.msgFwdConfig
export const selectChangeSuccess = (state: RootState) => state.config.value.changeSuccess
export const selectRebootChangeSuccess = (state: RootState) => state.config.value.rebootChangeSuccess
export const selectErrorState = (state: RootState) => state.config.value.errorState
export const selectFirmwareUpgradeAvailable = (state: RootState) => state.config.value.firmwareUpgradeAvailable
export const selectFirmwareUpgradeName = (state: RootState) => state.config.value.firmwareUpgradeName
export const selectFirmwareUpgradeMsg = (state: RootState) => state.config.value.firmwareUpgradeMsg
export const selectFirmwareUpgradeErr = (state: RootState) => state.config.value.firmwareUpgradeErr
export const selectDestIp = (state: RootState) => state.config.value.destIp
export const selectSnmpMsgType = (state: RootState) => state.config.value.snmpMsgType
export const selectIncludeSecurityHeader = (state: RootState) => state.config.value.includeSecurityHeader
export const selectLoading = (state: RootState) => state.config.loading
export const selectAddConfigPoint = (state: RootState) => state.config.value.addConfigPoint
export const selectConfigCoordinates = (state: RootState) => state.config.value.configCoordinates
export const selectConfigList = (state: RootState) => state.config.value.configList
export const selectMsgFwdConfigType = (state: RootState) => state.config.value.msgFwdConfigType

export const {
  setDestIp,
  setMsgType,
  setIncludeSecurityHeader,
  toggleConfigPointSelect,
  updateConfigPoints,
  clearConfig,
  clearFirmware,
} = configSlice.actions

export default configSlice.reducer
