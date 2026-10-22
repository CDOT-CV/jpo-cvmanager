import React, { useEffect } from 'react'
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
  deleteSnmpSet,
} from '../generalSlices/configSlice'
import { Button, Tooltip } from '@mui/material'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../store'
import toast from 'react-hot-toast'

const SnmpwalkMenu = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const store = useStore<RootState>()

  const msgFwdConfig = useSelector(selectMsgFwdConfig)
  const msgFwdConfigType = useSelector(selectMsgFwdConfigType)
  const error = useSelector(selectErrorState)
  const loading = useSelector(selectLoading)

  const isConfigEmpty = React.useMemo(() => {
    if (!msgFwdConfig) return true
    if (Object.hasOwn(msgFwdConfig, 'rsuXmitMsgFwdingTable') && Object.hasOwn(msgFwdConfig, 'rsuReceivedMsgTable')) {
      const txEmpty =
        !Object.hasOwn(msgFwdConfig, 'rsuXmitMsgFwdingTable') ||
        Object.keys(msgFwdConfig.rsuXmitMsgFwdingTable ?? {}).length === 0
      const rxEmpty =
        !Object.hasOwn(msgFwdConfig, 'rsuReceivedMsgTable') ||
        Object.keys(msgFwdConfig.rsuReceivedMsgTable ?? {}).length === 0
      return txEmpty && rxEmpty
    }
    return Object.keys(msgFwdConfig).length === 0
  }, [msgFwdConfig])

  const rsuIp = useSelector(selectRsuIpv4)
  const writeUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'write'))
  const refreshUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'live'))

  const cachedUnavailable = useSelector((state: RootState) => forwardingUnavailableReason(state, [rsuIp], 'cached'))

  useEffect(() => {
    // Refresh Data
    if (rsuIp && !cachedUnavailable) dispatch(getCachedSnmpFwdConfigsFromDatabase(rsuIp))
  }, [rsuIp, cachedUnavailable, dispatch])

  const handleDelete = (countsMsgType: string, ip: string) => {
    const buttons = [
      {
        label: 'Yes',
        onClick: () => {
          dispatch(
            deleteSnmpSet({
              ipList: [rsuIp],
              snmpMsgType: countsMsgType,
              destIp: ip,
            })
          ).then((data) => {
            if (deleteSnmpSet.fulfilled.match(data) && data.payload.changeSuccess) {
              toast.success('Successfully deleted SNMP forwarding')
            } else {
              const message = deleteSnmpSet.fulfilled.match(data) ? data.payload.errorState : data.error.message
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
      `Are you sure you want to delete SNMP forwarding for ${countsMsgType} messages to the IP: ${ip}? `,
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
      <div>
        {Object.hasOwn(msgFwdConfig, 'rsuXmitMsgFwdingTable') && Object.hasOwn(msgFwdConfig, 'rsuReceivedMsgTable') ? (
          <div>
            <h2 id="snmptxheader">TX Forward Table</h2>
            {Object.keys(msgFwdConfig.rsuXmitMsgFwdingTable ?? {}).map((index) => (
              <div key={'msgFwd-' + index}>
                <SnmpwalkItem
                  key={'snmptxitem-' + index}
                  content={msgFwdConfig.rsuXmitMsgFwdingTable[index]}
                  handleDelete={writeUnavailable ? undefined : handleDelete}
                  index={index}
                />
              </div>
            ))}

            <h2 id="snmprxheader">RX Forward Table</h2>
            {Object.keys(msgFwdConfig.rsuReceivedMsgTable ?? {}).map((index) => (
              <div key={index}>
                <SnmpwalkItem
                  key={'snmprxitem-' + index}
                  content={msgFwdConfig.rsuReceivedMsgTable[index]}
                  handleDelete={writeUnavailable ? undefined : handleDelete}
                  index={index}
                />
              </div>
            ))}
          </div>
        ) : (
          <div>
            {Object.keys(msgFwdConfig).map((index) => (
              <div key={index}>
                <SnmpwalkItem
                  key={'snmpitem-' + index}
                  content={msgFwdConfig[index]}
                  handleDelete={writeUnavailable ? undefined : handleDelete}
                  index={index}
                />
              </div>
            ))}
          </div>
        )}
      </div>
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
