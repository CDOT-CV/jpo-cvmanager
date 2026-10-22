import { useEffect } from 'react'
import SnmpwalkItem from './SnmpwalkItem'
import { useSelector, useDispatch, useStore } from 'react-redux'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from './AdminDeletionOptions'
import { selectRsuIpv4, forwardingUnavailableReason } from '../generalSlices/rsuSlice'
import {
  selectMsgFwdConfig,
  selectMsgFwdConfigType,
  selectErrorState,
  selectLoading,

  // Actions
  getCachedSnmpFwdConfigsFromDatabase,
  getRsuMsgConfigsFromRsu,
} from '../generalSlices/configSlice'
import RefreshIcon from '@mui/icons-material/Refresh'
import './css/SnmpwalkMenu.css'
import {
  // Actions
  deleteSnmpRow,
} from '../generalSlices/configSlice'
import { Button, Tooltip } from '@mui/material'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../store'
import toast from 'react-hot-toast'
import { RsuDsrcFwdConfigs, RsuMsgFwdConfigSingle } from '../models/RsuApi'

const SnmpwalkMenu = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const store = useStore<RootState>()

  const msgFwdConfig = useSelector(selectMsgFwdConfig)
  const msgFwdConfigType = useSelector(selectMsgFwdConfigType)
  const error = useSelector(selectErrorState)
  const loading = useSelector(selectLoading)

  // DSRC rows may coexist with RX/TX rows in the historical PostgreSQL snapshot.
  const legacyRows = Object.fromEntries(
    Object.entries(msgFwdConfig).filter(([key]) => /^\d+$/.test(key))
  ) as RsuDsrcFwdConfigs
  const receiveRows = msgFwdConfig.rsuReceivedMsgTable ?? {}
  const transmitRows = msgFwdConfig.rsuXmitMsgFwdingTable ?? {}
  const isConfigEmpty = [legacyRows, receiveRows, transmitRows].every((rows) => Object.keys(rows).length === 0)

  const rsuIp = useSelector(selectRsuIpv4)
  const writeUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'write'))
  const refreshUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'live'))

  const cachedUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'cached'))

  useEffect(() => {
    // Refresh Data
    if (rsuIp && !cachedUnavailable) dispatch(getCachedSnmpFwdConfigsFromDatabase(rsuIp))
  }, [rsuIp, cachedUnavailable, dispatch])

  const handleDelete = (row: RsuMsgFwdConfigSingle) => {
    if ((row.table !== 'RECEIVE' && row.table !== 'TRANSMIT') || !row.index) return
    const table = row.table
    const buttons = [
      {
        label: 'Yes',
        onClick: () => {
          dispatch(
            deleteSnmpRow({
              rsuIp,
              table,
              index: row.index,
              destIp: row.IP,
              port: row.Port,
            })
          ).then((data) => {
            if (deleteSnmpRow.fulfilled.match(data) && data.payload.changeSuccess) {
              toast.success('Successfully deleted SNMP forwarding')
              const current = store.getState()
              if (selectRsuIpv4(current) === rsuIp && !forwardingUnavailableReason(current, [rsuIp], 'cached')) {
                dispatch(getCachedSnmpFwdConfigsFromDatabase(rsuIp))
              }
            } else {
              const message = deleteSnmpRow.fulfilled.match(data) ? data.payload.errorState : data.error.message
              toast.error('Failed to delete SNMP forwarding: ' + (message || 'Request did not complete'))
            }
          })
        },
      },
      {
        label: 'No',
        onClick: () => {},
      },
    ]
    const alertOptions = Options(
      'Delete SNMP Configuration',
      `Are you sure you want to delete SNMP forwarding for ${row['Message Type']} messages to the IP: ${row.IP} (${table} row ${row.index})? `,
      buttons
    )

    confirmAlert(alertOptions)
  }

  if (cachedUnavailable) return <p>{cachedUnavailable}</p>

  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: '10px' }}>
        {error && <p role="alert">{error}</p>}
        {isConfigEmpty && !error && !loading && (
          <h5 className="museo-slab">No message forwarding configurations were found.</h5>
        )}
      </div>
      {[
        { title: 'TX Forward Table', rows: transmitRows, editable: !writeUnavailable },
        { title: 'RX Forward Table', rows: receiveRows, editable: !writeUnavailable },
        { title: 'Legacy RSU 4.1 Forward Table (cached, read only)', rows: legacyRows, editable: false },
      ].map(
        ({ title, rows, editable }) =>
          Object.keys(rows).length > 0 && (
            <div key={title}>
              <h2>{title}</h2>
              {Object.entries(rows as RsuDsrcFwdConfigs).map(([index, content]) => (
                <SnmpwalkItem
                  key={index}
                  content={content}
                  index={index}
                  handleDelete={
                    editable && (content.table === 'RECEIVE' || content.table === 'TRANSMIT') && content.index
                      ? handleDelete
                      : undefined
                  }
                />
              ))}
            </div>
          )
      )}
      {refreshUnavailable && <p>{refreshUnavailable}</p>}
      {!refreshUnavailable && (
        <div id="msgfwddiv">
          <Tooltip title="Refresh Message Forwarding">
            <Button
              startIcon={<RefreshIcon />}
              variant="outlined"
              onClick={() => {
                dispatch(getRsuMsgConfigsFromRsu(rsuIp)).then((data) => {
                  const current = store.getState()
                  // A late failure must not reload the previously selected RSU's cache.
                  if (
                    selectRsuIpv4(current) !== rsuIp ||
                    current.config.value.forwardingReadRequestId !== data.meta.requestId
                  )
                    return
                  if (getRsuMsgConfigsFromRsu.rejected.match(data)) {
                    toast.error('Failed to fetch RSU message forwarding configuration, loading cached data')
                    dispatch(getCachedSnmpFwdConfigsFromDatabase(rsuIp))
                  } else if (getRsuMsgConfigsFromRsu.fulfilled.match(data)) {
                    toast.success('Successfully fetched RSU message forwarding configuration')
                  }
                })
              }}
              size="large"
              sx={{
                marginTop: '20px',
              }}
              color="info"
              className="museo-slab capital-case"
            >
              Refresh Message Forwarding
            </Button>
          </Tooltip>
        </div>
      )}
      <br />
      <h3 className="museo-slab">Source: {msgFwdConfigType === 'database' ? 'Cached (Database)' : 'Live (RSU)'}</h3>
    </div>
  )
}

export default SnmpwalkMenu
