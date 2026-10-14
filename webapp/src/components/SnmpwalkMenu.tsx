import React, { useEffect } from 'react'
import SnmpwalkItem from './SnmpwalkItem'
import { useSelector, useDispatch } from 'react-redux'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from './AdminDeletionOptions'
import { selectRsuIpv4 } from '../generalSlices/rsuSlice'
import {
  selectMsgFwdConfig,
  selectMsgFwdConfigType,

  // Actions
  getRsuMsgConfigsFromRsu,
  resetMsgFwdState,
} from '../generalSlices/configSlice'
import { selectOrganizationName } from '../generalSlices/userSlice'
import {
  RSU_MSG_FWD_QUERY_TAG,
  rsuMsgFwdQueryApiSlice,
  useGetRsuMsgFwdConfigsQuery,
} from '../features/api/rsuMsgFwdQueryApiSlice'
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

  const liveMsgFwdConfig = useSelector(selectMsgFwdConfig)
  const msgFwdConfigType = useSelector(selectMsgFwdConfigType)
  const organization = useSelector(selectOrganizationName)
  const rsuIp = useSelector(selectRsuIpv4)

  const { data: cachedMsgFwdConfig = {}, refetch: refetchCachedMsgFwdConfig } = useGetRsuMsgFwdConfigsQuery(
    { organization, rsuIp },
    // Always re-read the database when the menu opens, as the previous thunk did
    { skip: !organization || !rsuIp, refetchOnMountOrArgChange: true }
  )
  // Live configs come from the RSU via the Python /rsu-msgfwd-fetch endpoint; cached configs come from the database
  const msgFwdConfig = msgFwdConfigType === 'rsu' ? liveMsgFwdConfig : cachedMsgFwdConfig

  const isConfigEmpty = React.useMemo(() => {
    if (!msgFwdConfig) return true
    if (Object.hasOwn(msgFwdConfig, 'rsuXmitMsgFwdingTable') && Object.hasOwn(msgFwdConfig, 'rsuReceivedMsgTable')) {
      const txEmpty = !Object.hasOwn(msgFwdConfig, 'rsuXmitMsgFwdingTable') ||
        Object.keys(msgFwdConfig.rsuXmitMsgFwdingTable ?? {}).length === 0
      const rxEmpty = !Object.hasOwn(msgFwdConfig, 'rsuReceivedMsgTable') ||
        Object.keys(msgFwdConfig.rsuReceivedMsgTable ?? {}).length === 0
      return txEmpty && rxEmpty
    }
    return Object.keys(msgFwdConfig).length === 0
  }, [msgFwdConfig])

  useEffect(() => {
    // Show cached data for the newly selected RSU
    dispatch(resetMsgFwdState())
  }, [rsuIp, dispatch])

  const refreshCachedMsgFwdConfig = () => {
    dispatch(resetMsgFwdState())
    if (organization && rsuIp) refetchCachedMsgFwdConfig()
  }

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
          ).then((data: any) => {
            if (data.payload.changeSuccess) {
              dispatch(rsuMsgFwdQueryApiSlice.util.invalidateTags([{ type: RSU_MSG_FWD_QUERY_TAG, id: rsuIp }]))
              toast.success('Successfully deleted SNMP forwarding')
            } else {
              toast.error('Failed to delete SNMP forwarding: ' + data.payload.errorState)
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

  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: '10px' }}>
        {isConfigEmpty && (
          <h5 className="museo-slab">No message forwarding configurations were found. This may indicate that none are configured or that there was an error retrieving them. Verify that this RSU is configured and refresh.</h5>
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
                  handleDelete={handleDelete}
                  index={index}
                />
              </div>
            ))}

            <h2 id="snmprxheader">RX Forward Table</h2>
            {Object.keys(msgFwdConfig.rsuReceivedMsgTable ?? {}).map((index) => (
              <div>
                <SnmpwalkItem
                  key={'snmprxitem-' + index}
                  content={msgFwdConfig.rsuReceivedMsgTable[index]}
                  handleDelete={handleDelete}
                  index={index}
                />
              </div>
            ))}
          </div>
        ) : (
          <div>
            {Object.keys(msgFwdConfig).map((index) => (
              <div>
                <SnmpwalkItem
                  key={'snmpitem-' + index}
                  content={msgFwdConfig[index]}
                  handleDelete={handleDelete}
                  index={index}
                />
              </div>
            ))}
          </div>
        )}
      </div>
      <div id="msgfwddiv">
        <Tooltip title="Refresh Message Forwarding">
          <Button
            startIcon={<RefreshIcon />}
            variant="outlined"
            onClick={() => {
              dispatch(getRsuMsgConfigsFromRsu(rsuIp)).then((data: any) => {
                if (getRsuMsgConfigsFromRsu.rejected.match(data)) {
                  toast.error('Failed to fetch RSU message forwarding configuration, loading cached data')
                  refreshCachedMsgFwdConfig()
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
      <br />
      <h3 className="museo-slab">
        Source: {msgFwdConfigType === 'database' ? 'Cached (Database)' : 'Live (RSU)'}
      </h3>
    </div>
  )
}

export default SnmpwalkMenu
