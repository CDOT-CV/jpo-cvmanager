import { useRef, useState } from 'react'
import { useSelector, useDispatch } from 'react-redux'

import { selectRsuData } from '../../generalSlices/rsuSlice'

import '../../components/css/SnmpwalkMenu.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import MaterialTable, { Action } from '@material-table/core'
import { RootState } from '../../store'
import { useGetScmsStatusQuery } from '../api/scmsApiSlice'
import { useGetRsuLastOnlineQuery, useGetRsuOnlineStatusesQuery } from '../api/rsuOnlineStatusApiSlice'
import { selectOrganizationName } from '../../generalSlices/userSlice'

import GpsFixedSharp from '@mui/icons-material/GpsFixedSharp'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'

import AdminTable from '../../components/AdminTable'
import { setMapViewState } from '../../pages/mapSlice'
import { Alert, Accordion, AccordionDetails, AccordionSummary, Box, Button, Stack, Typography, useTheme } from '@mui/material'
import { RsuInfo } from '../../models/RsuApi'
import { useReactToPrint } from 'react-to-print'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { toggleMapMenuSelection } from './menuSlice'
import toast from 'react-hot-toast'
import { useSendRsuErrorSummaryEmailMutation } from '../api/emailApiSlice'
import { selectEmail } from '../../generalSlices/userSlice'

import {
  getOnlineStatus,
  getScmsStatus,
  getStatusLabel,
  getStatusColor,
  getScmsExpirationDisplay,
  getLastOnlineDisplay,
  hasConfirmedRsuError,
  OnlineStatus,
  ScmsStatus,
} from '../../utils/rsu-status'

const DisplayRsuErrors = ({ initialSelectedRsu }: { initialSelectedRsu?: RsuInfo }) => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const rsuData = useSelector(selectRsuData)
  const organization = useSelector(selectOrganizationName)
  const onlineStatusQuery = useGetRsuOnlineStatusesQuery(organization, { skip: !organization })
  const scmsStatusQuery = useGetScmsStatusQuery(organization, { skip: !organization })
  const [selectedRSU, setSelectedRSU] = useState<RsuInfo | undefined>(initialSelectedRsu)
  const selectedRsuIp = selectedRSU?.properties.ipv4_address ?? ''
  const lastOnlineQuery = useGetRsuLastOnlineQuery(selectedRsuIp, {
    skip: !selectedRsuIp,
  })
  const contentRef = useRef(null)
  const errorRef = useRef(null)
  const userEmail = useSelector(selectEmail)
  const handlePrint = useReactToPrint({ contentRef })
  const handleErrorPrint = useReactToPrint({ contentRef: errorRef })
  const [submitRsuErrorSummary] = useSendRsuErrorSummaryEmailMutation()

  const theme = useTheme()

  type RsuErrorRowType = {
    rsu: string
    online_status: OnlineStatus
    lat: number
    lon: number
    scms_status: ScmsStatus
  }

  const getRSUOnlineStatus = (ip: string) => getOnlineStatus(ip, onlineStatusQuery)
  const getRSUSCMSStatus = (ip: string) => getScmsStatus(ip, scmsStatusQuery)
  const getRSUSCMSExpirationDisplay = (ip: string) => getScmsExpirationDisplay(ip, scmsStatusQuery)
  const getRSULastOnline = () => getLastOnlineDisplay(lastOnlineQuery)

  const getRSUSCMSDisplay = (ip: string) => {
    const status = getRSUSCMSStatus(ip)
    const expiration = getRSUSCMSExpirationDisplay(ip)
    const expirationDate = scmsStatusQuery.currentData?.[ip]?.expiration
    const expired = status === 'unhealthy' && expirationDate && new Date(expirationDate).getTime() < Date.now()
    return `SCMS ${getStatusLabel(status)}${expired ? ' (RSU SCMS certificate expired)' : ''} (Certificate expiration: ${expiration})`
  }

  const sendRsuErrorSummaryEmail = async (rsu: string, online_status: string, scms_status: string) => {
    const messageTable = `
        <table>
            <tr>
                <th>Online Status</th>
                <th>SCMS Status</th>
            </tr>
            <tr>
                <td>${'RSU ' + online_status}</td>
                <td>${scms_status}</td>
            </tr>
        </table>
    `

    const message = `
        <p>Below is the error summary for RSU ${rsu} at ${new Date().toISOString()} UTC:</p>
        ${messageTable}
    `

    const data: RsuErrorSummaryEmailContents = {
      subject: `RSU Error Summary for ${rsu}`,
      message: message,
    }

    toast.promise(submitRsuErrorSummary(data).unwrap(), {
      loading: `Sending RSU error summary email to ${userEmail}...`,
      success: `RSU error summary email sent successfully to ${userEmail}!`,
      error: `Failed to send RSU error summary email to ${userEmail}`,
    })
  }

  // Create RSU Errors Table Data
  const rsuTableData = rsuData.map((rsu) => {
    return {
      rsu: rsu.properties.ipv4_address,
      road: rsu.properties.primary_route,
      lat: rsu.geometry.coordinates[1],
      lon: rsu.geometry.coordinates[0],
      online_status: getRSUOnlineStatus(rsu.properties.ipv4_address),
      scms_status: getRSUSCMSStatus(rsu.properties.ipv4_address),
      cert_expiration: getRSUSCMSExpirationDisplay(rsu.properties.ipv4_address),
      milepost: rsu.properties.milepost,
      primary_route: rsu.properties.primary_route,
    }
  })

  const tableActions: Action<RsuErrorRowType>[] = [
    {
      icon: () => null,
      iconProps: {
        title: 'Print Full Report',
        color: 'info',
        itemType: 'outlined',
      },
      position: 'toolbar',
      onClick: () => {
        handlePrint()
      },
    },
    {
      icon: () => null,
      iconProps: {
        title: 'Print Error Report',
        color: 'info',
        itemType: 'outlined',
      },
      position: 'toolbar',
      onClick: () => {
        handleErrorPrint()
      },
    },
  ]

  const containerStyle = {
    display: 'flex',
    justifyContent: 'center',
    margin: 'auto',
    TextAlign: 'center',
    flexDirection: 'column' as const,
    width: '100%',
    height: '100%',
    backgroundColor: theme.palette.custom.mapLegendBackground,
    borderRadius: '4px',
  }

  return (
    <div style={containerStyle}>
      <div>
        {onlineStatusQuery.isError && (
          <Alert severity="warning" action={<Button disabled={onlineStatusQuery.isFetching} onClick={() => onlineStatusQuery.refetch()}>Retry</Button>}>
            Online status unavailable.
          </Alert>
        )}
        {scmsStatusQuery.isError && (
          <Alert severity="warning" action={<Button disabled={scmsStatusQuery.isFetching} onClick={() => scmsStatusQuery.refetch()}>Retry</Button>}>
            SCMS status unavailable.
          </Alert>
        )}
      </div>
      {selectedRSU !== undefined ? (
        <Stack direction="column" spacing={2} sx={{ pl: 1, pr: 1, width: '100%' }}>
          <SideBarHeader
            onClick={() => {
              setSelectedRSU(undefined)
            }}
            title={selectedRSU.properties.ipv4_address + ' Status'}
          />
          <div className="accordion">
            <Accordion elevation={0}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography>Online Status</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <div>
                  <Typography fontSize="small" sx={{ color: theme.palette.text.secondary }}>
                    <b>RSU Online Status: </b>
                    {getStatusLabel(getRSUOnlineStatus(selectedRSU.properties.ipv4_address))}
                  </Typography>
                  <Typography fontSize="small" sx={{ color: theme.palette.text.secondary }}>
                    <b>RSU Last Online: </b>
                    {getRSULastOnline()}
                  </Typography>
                </div>
              </AccordionDetails>
            </Accordion>
            <Accordion elevation={0}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography>SCMS Status</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <div>
                  <Typography fontSize="small" sx={{ color: theme.palette.text.secondary }}>
                    <b>SCMS Status: </b>
                    {getStatusLabel(getRSUSCMSStatus(selectedRSU.properties.ipv4_address))}
                  </Typography>
                  <Typography fontSize="small" sx={{ color: theme.palette.text.secondary }}>
                    <b>SCMS Expiration: </b>
                    {getRSUSCMSExpirationDisplay(selectedRSU.properties.ipv4_address)}
                  </Typography>
                </div>
              </AccordionDetails>
            </Accordion>
          </div>
          <div style={{ display: 'flex', alignContent: 'center', justifyContent: 'flex-start', flexDirection: 'row' }}>
            <Button
              variant="contained"
              style={{ margin: '1rem' }}
              onClick={() => {
                const lastOnlineStatus =
                  getStatusLabel(getRSUOnlineStatus(selectedRSU.properties.ipv4_address)) +
                  ' (Last Online: ' + getRSULastOnline() + ')'

                return sendRsuErrorSummaryEmail(
                  selectedRSU.properties.ipv4_address,
                  lastOnlineStatus,
                  getRSUSCMSDisplay(selectedRSU.properties.ipv4_address)
                )
              }}
              className="museo-slab"
            >
              Send Error Summary Email
            </Button>
          </div>
        </Stack>
      ) : (
        <Stack direction="column" spacing={2} sx={{ pl: 1, pr: 1 }}>
          <SideBarHeader onClick={() => dispatch(toggleMapMenuSelection('Display RSU Status'))} title="RSU Status" />
          <AdminTable
            actions={tableActions}
            columns={[
              {
                title: 'Location',
                field: 'milepost',
                width: '30%',
                render: (rowData) => (
                  <Button
                    onClick={() => {
                      dispatch(setMapViewState({ latitude: rowData.lat, longitude: rowData.lon, zoom: 15 }))
                      setSelectedRSU(rsuData.find((rsu) => rsu.properties.ipv4_address === rowData.rsu))
                    }}
                    variant="text"
                    endIcon={<GpsFixedSharp />}
                    color="info"
                    sx={{
                      '&:hover': {
                        backgroundColor: 'transparent',
                      },
                    }}
                  >
                    <Typography
                      fontSize="small"
                      sx={{
                        '&:hover': {
                          textDecoration: 'underline',
                        },
                      }}
                    >
                      {rowData.primary_route} Mile {rowData.milepost}
                    </Typography>
                  </Button>
                ),
              },
              {
                title: 'Online Status',
                field: 'online_status',
                render: (rowData) => (
                  <Box
                    style={{
                      color: theme.palette.getContrastText(getStatusColor(rowData.online_status, theme)),
                      backgroundColor: getStatusColor(rowData.online_status, theme),
                      minWidth: '4rem',
                      padding: '0 8px',
                      height: '1.5rem',
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center',
                      borderRadius: '1rem',
                    }}
                  >
                    <Typography fontSize="medium">{getStatusLabel(rowData.online_status)}</Typography>
                  </Box>
                ),
              },
              {
                title: 'SCMS Status',
                field: 'scms_status',
                render: (rowData) => (
                  <>
                    <Typography
                      fontSize="medium"
                      sx={{
                        color: getStatusColor(rowData.scms_status, theme, 'light'),
                      }}
                    >
                      {getStatusLabel(rowData.scms_status)}
                    </Typography>
                    <Typography fontSize="small" sx={{ color: theme.palette.text.primary }}>
                      {rowData.cert_expiration}
                    </Typography>
                  </>
                ),
              },
              { title: 'RSU IP', field: 'rsu' },
            ]}
            data={rsuTableData}
            title=""
            selection={false}
            defaultPageSize={5}
            tableLayout="auto"
            pageSizeOptions={[]}
          />
        </Stack>
      )}
      <div style={{ display: 'none' }}>
        <div
          ref={contentRef}
          style={{
            margin: '50px',
            fontFamily: 'Arial Helvetica Sans-Serif',
            height: '100vh',
          }}
        >
          <h1 style={{ textAlign: 'center', marginBottom: '10px', color: 'black' }}>RSU Summary</h1>
          <br />
          <p style={{ color: 'black' }}>
            Below is the generated RSU summary report for all RSUs at {new Date().toISOString()} UTC:
          </p>
          <div style={{ marginTop: '25px' }}>
            <MaterialTable
              columns={[
                {
                  title: 'RSU',
                  field: 'rsu',
                  render: (rowData) => <p style={{ color: 'black', fontWeight: 'bold' }}>{rowData.rsu}</p>,
                },
                {
                  title: 'Road',
                  field: 'road',
                  render: (rowData) => <p style={{ color: 'black', fontWeight: 'bold' }}>{rowData.road}</p>,
                },
                {
                  title: 'Online Status',
                  field: 'online_status',
                  render: (rowData) => (
                    <Typography
                      fontSize="medium"
                      sx={{
                        color: getStatusColor(rowData.online_status, theme),
                        fontWeight: 'bold',
                      }}
                    >
                      {getStatusLabel(rowData.online_status)}
                    </Typography>
                  ),
                },
                {
                  title: 'SCMS Status',
                  field: 'scms_status',
                  render: (rowData) => (
                    <>
                      <Typography
                        fontSize="medium"
                        sx={{
                          color: getStatusColor(rowData.scms_status, theme),
                          fontWeight: 'bold',
                        }}
                      >
                        {getStatusLabel(rowData.scms_status)}
                      </Typography>
                      <Typography fontSize="small" sx={{ color: 'black' }}>
                        {rowData.cert_expiration}
                      </Typography>
                    </>
                  ),
                },
              ].map((column) => ({
                ...column,
                cellStyle: {
                  borderRight: '1px solid black', // Add column lines
                },
              }))}
              actions={[]}
              data={rsuTableData}
              title=""
              options={{
                toolbar: false,
                search: false,
                paging: false,
                rowStyle: {
                  overflowWrap: 'break-word',
                  border: `1px solid black`, // Add cell borders
                },
              }}
              style={{
                backgroundColor: 'white',
                color: 'black',
                fontFamily: 'Arial Helvetica Sans-Serif',
                border: 'none',
              }}
            />
          </div>
        </div>
        <div
          ref={errorRef}
          style={{
            margin: '50px',
            fontFamily: 'Arial Helvetica Sans-Serif',
            height: '100vh',
          }}
        >
          <h1 style={{ textAlign: 'center', marginBottom: '10px', color: 'black' }}>RSU Error Summary</h1>
          <br />
          <p style={{ color: 'black' }}>
            {`Below is the generated RSU Error summary report at ${new Date().toISOString()} UTC. ` +
              'Only confirmed offline, unstable, or SCMS unhealthy devices are included. ' +
              'Loading, unknown, and unavailable statuses alone do not qualify as device errors; ' +
              'see the full report for all devices.'}
          </p>
          <div style={{ marginTop: '25px' }}>
            <MaterialTable
              columns={[
                {
                  title: 'RSU',
                  field: 'rsu',
                  render: (rowData) => <p style={{ color: 'black', fontWeight: 'bold' }}>{rowData.rsu}</p>,
                },
                {
                  title: 'Road',
                  field: 'road',
                  render: (rowData) => <p style={{ color: 'black', fontWeight: 'bold' }}>{rowData.road}</p>,
                },
                {
                  title: 'Online Status',
                  field: 'online_status',
                  render: (rowData) => (
                    <Typography
                      fontSize="medium"
                      sx={{
                        color: getStatusColor(rowData.online_status, theme),
                        fontWeight: 'bold',
                      }}
                    >
                      {getStatusLabel(rowData.online_status)}
                    </Typography>
                  ),
                },
                {
                  title: 'SCMS Status',
                  field: 'scms_status',
                  render: (rowData) => (
                    <>
                      <Typography
                        fontSize="medium"
                        sx={{
                          color: getStatusColor(rowData.scms_status, theme),
                          fontWeight: 'bold',
                        }}
                      >
                        {getStatusLabel(rowData.scms_status)}
                      </Typography>
                      <Typography fontSize="small" sx={{ color: 'black' }}>
                        {rowData.cert_expiration}
                      </Typography>
                    </>
                  ),
                },
              ].map((column) => ({
                ...column,
                cellStyle: {
                  borderRight: '1px solid black', // Add column lines
                },
              }))}
              actions={[]}
              data={
                rsuTableData !== undefined
                  ? rsuTableData.filter((row) => hasConfirmedRsuError(row.online_status, row.scms_status))
                  : []
              }
              title=""
              options={{
                toolbar: false,
                search: false,
                paging: false,
                rowStyle: {
                  overflowWrap: 'break-word',
                  border: `1px solid black`, // Add cell borders
                },
              }}
              style={{
                backgroundColor: 'white',
                color: 'black',
                fontFamily: 'Arial Helvetica Sans-Serif',
                border: 'none',
              }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

export default DisplayRsuErrors
