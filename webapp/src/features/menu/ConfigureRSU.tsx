import React, { useMemo, useState } from 'react'
import SnmpwalkMenu from '../../components/SnmpwalkMenu'
import SnmpsetMenu from '../../components/SnmpsetMenu'
import MapMenuInfo from '../../components/MapMenuInfo'
import RsuRebootMenu from '../../components/RsuRebootMenu'
import RsuFirmwareMenu from '../../components/RsuFirmwareMenu'
import Accordion, { AccordionProps } from '@mui/material/Accordion'
import AccordionDetails from '@mui/material/AccordionDetails'
import AccordionSummary from '@mui/material/AccordionSummary'
import { shallowEqual, useDispatch, useSelector } from 'react-redux'
import { Alert, Box, CircularProgress, useTheme, Paper, Grid2, IconButton, Divider, Chip, Stack } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Typography from '@mui/material/Typography'
import { selectSelectedRsu, selectRsu, forwardingUnavailableReason, legacyForwardingIps } from '../../generalSlices/rsuSlice'
import { clearConfig, selectConfigList } from '../../generalSlices/configSlice'
import { selectCountsEndDate, selectCountsStartDate } from './menuSlice'
import '../../components/css/SnmpwalkMenu.css'
import { RootState } from '../../store'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import CloseIcon from '@mui/icons-material/Close'
import RoomOutlined from '@mui/icons-material/RoomOutlined'
import { headerTabHeight } from '../../styles'
import { getRsuCountsErrorMessage, useGetRsuCountsByIpQuery } from '../api/rsuCountsApiSlice'
import EnvironmentVars from '../../EnvironmentVars'
import { buildRsuCountTableRows } from './rsuCountTable'
import { RsuMessageCountsTable } from './RsuMessageCountsTable'

const ConfigMenu = ({ children }) => {
  return <Box>{children}</Box>
}

const messageCountsHelp = "Counts show messages received from this RSU through message forwarding during the selected time range."
const forwardingHelp = "Message forwarding sends the RSU's V2X messages to another system. Choose a destination IPv4 address and message type, the corresponding forwarding table and UDP port will be automatically handled. Security Header includes IEEE 1609.2 security headers."
const singleFirmwareHelp = 'Check whether a firmware upgrade is available for this RSU. An upgrade can take 5 to 60 minutes and continues if you close CV Manager.'
const batchFirmwareHelp = 'Check for available firmware upgrades for the selected RSUs. Devices already up to date are skipped and offline devices are scheduled for upgrade when they reconnect. Upgrades can take 5 to 60 minutes and continue if you close CV Manager.'
const rebootHelp = 'Rebooting takes the RSU offline and interrupts message forwarding. This feature supports Commsignia RSUs. An accepted request does not confirm that the device has restarted.'

// Help is a sibling of the accordion, so it cannot trigger its toggle button.
const RsuMenuAccordion = ({ id, title, help, children, ...props }: AccordionProps & { id: string; title: string; help?: string }) => (
  <Box sx={{ position: 'relative' }}>
    <Accordion {...props} id={id} disableGutters elevation={0} sx={{ '&::before': { display: 'none' } }}>
      <AccordionSummary id={`${id}-header`} aria-controls={`${id}-content`}
        sx={{ '& .MuiAccordionSummary-content': { mr: help ? 4 : 1 } }} expandIcon={<ExpandMoreIcon />}>
        <Typography>{title}</Typography>
      </AccordionSummary>
      <AccordionDetails>{children}</AccordionDetails>
    </Accordion>
    {help && (
      <Box sx={{ position: 'absolute', top: 10, right: 42 }}>
        <MapMenuInfo title={title.endsWith('Forwarding') ? 'About message forwarding' : `About ${title.toLowerCase()}`}>
          {help}
        </MapMenuInfo>
      </Box>
    )}
  </Box>
)

const ConfigureRSU = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const theme = useTheme()

  const [expanded, setExpanded] = useState<string | undefined>(undefined)
  const handleChange = (panel: string) => (event: React.SyntheticEvent<Element, Event>, isExpanded: boolean) => {
    setExpanded(isExpanded ? panel : undefined)
  }
  const selectedRsu = useSelector(selectSelectedRsu)
  const selectedConfigList = useSelector(selectConfigList)
  const countsStartDate = useSelector(selectCountsStartDate)
  const countsEndDate = useSelector(selectCountsEndDate)
  const rsuIp = selectedRsu?.properties?.ipv4_address
  const skippedIps = useSelector((state: RootState) => legacyForwardingIps(state, selectedConfigList), shallowEqual)
  const forwardingIps = selectedConfigList.filter((ip) => !skippedIps.includes(ip))
  const forwardingReason = useSelector((state: RootState) =>
    forwardingUnavailableReason(state, rsuIp ? [rsuIp] : forwardingIps, 'write')
  )
  const messageTypes = useMemo(() => EnvironmentVars.getMessageTypes(), [])
  const maxDurationMs = EnvironmentVars.MAX_QUERY_DURATION_DAYS * 24 * 60 * 60 * 1000
  const queryDurationExceeded = countsEndDate.getTime() - countsStartDate.getTime() > maxDurationMs

  const {
    data: rsuMessageCounts,
    isFetching,
    isError,
    error,
  } = useGetRsuCountsByIpQuery(
    { rsuIp: rsuIp ?? '', startDate: countsStartDate, endDate: countsEndDate, messages: messageTypes },
    { skip: !rsuIp || queryDurationExceeded }
  )

  const countTableRows = useMemo(
    () => buildRsuCountTableRows(messageTypes, rsuMessageCounts),
    [messageTypes, rsuMessageCounts]
  )

  return (
    <Paper
      sx={{
        lineHeight: 1.1,
        backgroundColor: theme.palette.background.paper,
        '& .MuiAccordionDetails-root': { px: 2, pt: 0, pb: 1 },
      }}
    >
      {selectedRsu && (
        <div>
          <IconButton
            sx={{
              position: 'absolute',
              zIndex: 100,
              top: '10px',
              right: '10px',
            }}
            onClick={() => {
              dispatch(selectRsu(null))
            }}
          >
            <CloseIcon />
          </IconButton>
          <Grid2
            container
            columnSpacing={0.5}
            rowSpacing={0.5}
            sx={{
              color: theme.palette.text.primary,
              backgroundColor: theme.palette.background.paper,
              width: '100%',
              marginTop: '10px',
            }}
          >
            <Grid2 size={1} justifyContent="flex-start" sx={{ ml: '16px' }}>
              <RoomOutlined color="info" fontSize="medium" />
            </Grid2>
            <Grid2 size={8} justifyContent="flex-start">
              <Typography fontSize="large" className="museo-slab">
                {selectedRsu.properties.primary_route} Milepost {selectedRsu.properties.milepost}
              </Typography>
            </Grid2>
            <Grid2 size={4} justifyContent="flex-start" sx={{ ml: '16px' }}>
              <Typography fontSize="medium">IPv4: {selectedRsu.properties.ipv4_address}</Typography>
            </Grid2>
          </Grid2>
        </div>
      )}

      {selectedRsu && (
        <Box
          sx={{
            maxHeight: `calc(100vh - ${headerTabHeight + 185}px)`,
            overflowY: 'auto',
            height: 'fit-content',
            scrollbarColor: `${theme.palette.text.primary} ${theme.palette.background.paper}`,
            color: theme.palette.text.secondary,
            // Explicit dividers keep section boundaries consistent.
            '& > .MuiAccordion-root::before': { display: 'none' },
          }}
        >
          <RsuMenuAccordion id="selected-rsu-message-counts" title="Message Counts" help={messageCountsHelp}
            expanded={expanded === 'selected-rsu-message-counts'} onChange={handleChange('selected-rsu-message-counts')}>
            {queryDurationExceeded ? (
              <Typography role="alert">
                Query duration exceeds the maximum of {EnvironmentVars.MAX_QUERY_DURATION_DAYS} days. Please select a
                shorter time range.
              </Typography>
            ) : isFetching ? (
              <Box display="flex" justifyContent="center" py={2}>
                <CircularProgress size={24} />
              </Box>
            ) : isError ? (
              <Typography role="alert">{getRsuCountsErrorMessage(error)}</Typography>
            ) : (
              <RsuMessageCountsTable rows={countTableRows} />
            )}
          </RsuMenuAccordion>
          <Divider />
          <RsuMenuAccordion id="selected-rsu-current-config" title="View Forwarding" help={forwardingHelp}
            expanded={expanded === 'selected-rsu-current-config'} onChange={handleChange('selected-rsu-current-config')}>
            <ConfigMenu>
              <SnmpwalkMenu />
            </ConfigMenu>
          </RsuMenuAccordion>
          {!forwardingReason && <Divider sx={{ borderColor: 'divider' }} />}
          {!forwardingReason && (
            <RsuMenuAccordion id="selected-rsu-add-msg-forwarding" title="Configure Forwarding"
              expanded={expanded === 'selected-rsu-add-msg-forwarding'} onChange={handleChange('selected-rsu-add-msg-forwarding')}>
              <ConfigMenu>
                <SnmpsetMenu type="single_rsu" rsuIpList={[selectedRsu.properties.ipv4_address]} />
              </ConfigMenu>
            </RsuMenuAccordion>
          )}
          <Divider />
          <RsuMenuAccordion id="selected-rsu-firmware" title="Firmware" help={singleFirmwareHelp}
            expanded={expanded === 'selected-rsu-firmware'} onChange={handleChange('selected-rsu-firmware')}>
            <ConfigMenu>
              <RsuFirmwareMenu type="single_rsu" rsuIpList={[selectedRsu.properties.ipv4_address]} />
            </ConfigMenu>
          </RsuMenuAccordion>
          {selectedRsu.properties.manufacturer_name?.toLowerCase() === 'commsignia' && (
            <>
              <Divider />
              <RsuMenuAccordion id="selected-rsu-reboot" title="Reboot" help={rebootHelp}
                expanded={expanded === 'selected-rsu-reboot'} onChange={handleChange('selected-rsu-reboot')}>
                <ConfigMenu>
                  <RsuRebootMenu />
                </ConfigMenu>
              </RsuMenuAccordion>
            </>
          )}
        </Box>
      )}
      {selectedConfigList.length > 0 && !selectedRsu && (
        <Box>
          <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <RoomOutlined fontSize="medium" color="info" sx={{ flexShrink: 0 }} />
              <Typography fontSize="large" className="museo-slab" sx={{ flex: 1, minWidth: 0 }}>
                {selectedConfigList.length} {selectedConfigList.length === 1 ? 'RSU' : 'RSUs'} selected
              </Typography>
              <IconButton size="small" aria-label="Clear selected RSUs" onClick={() => dispatch(clearConfig())}
                sx={{ flexShrink: 0 }}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Stack>
            <Box component="ul" aria-label="Selected RSUs" sx={{
              display: 'flex', flexWrap: 'wrap', gap: 0.5, listStyle: 'none', p: 0, mt: 1, mb: 0,
              maxHeight: 88, overflowY: 'auto',
            }}>
              {selectedConfigList.map((ip) => (
                <Box component="li" key={ip}>
                  <Chip label={ip} size="small" variant="outlined" sx={{ fontWeight: 400, fontSize: '0.75rem' }} />
                </Box>
              ))}
            </Box>
          </Box>
          <Box
            sx={{
              maxHeight: `calc(100vh - ${headerTabHeight + 185}px)`,
              overflowY: 'auto',
              height: 'fit-content',
              color: theme.palette.text.secondary,
              '& > .MuiAccordion-root::before': { display: 'none' },
            }}
          >
            {forwardingReason ? (
              <Alert severity="warning" sx={{ m: 2, alignItems: 'center' }}>{forwardingReason}</Alert>
            ) : (
              <RsuMenuAccordion id="multiple-rsu-add-msg-forwarding" title="Configure Forwarding" help={forwardingHelp}
                expanded={expanded === 'multiple-rsu-add-msg-forwarding'} onChange={handleChange('multiple-rsu-add-msg-forwarding')}>
                <ConfigMenu>
                  <SnmpsetMenu type="multi_rsu" rsuIpList={forwardingIps} skippedIps={skippedIps} />
                </ConfigMenu>
              </RsuMenuAccordion>
            )}
            <Divider />
            <RsuMenuAccordion id="multiple-rsu-firmware" title="Firmware" help={batchFirmwareHelp}
              expanded={expanded === 'multiple-rsu-firmware'} onChange={handleChange('multiple-rsu-firmware')}>
              <ConfigMenu>
                <RsuFirmwareMenu type="multi_rsu" rsuIpList={selectedConfigList} />
              </ConfigMenu>
            </RsuMenuAccordion>
          </Box>
        </Box>
      )}
    </Paper>
  )
}

export default ConfigureRSU
