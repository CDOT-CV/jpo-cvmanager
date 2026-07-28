import React, { useMemo } from 'react'
import { useSelector, useDispatch } from 'react-redux'
import dayjs from 'dayjs'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs'
import BounceLoader from 'react-spinners/BounceLoader'

import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker'
import {
  selectCountsEndDate,
  selectCountsMsgType,
  selectCountsStartDate,
  setCountsEndDate,
  setCountsMsgType,
  setCountsStartDate,
  toggleMapMenuSelection,
} from './menuSlice'

import '../../components/css/SnmpwalkMenu.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../store'
import { CountsListElement } from '../../models/Rsu'
import { MessageType } from '../../models/MessageTypes'
import { Box, FormControl, InputLabel, MenuItem, Paper, Select, Stack, Typography, useTheme } from '@mui/material'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { useGetRsuCountsQuery } from '../api/rsuCountsApiSlice'
import { selectOrganizationName } from '../../generalSlices/userSlice'
import EnvironmentVars from '../../EnvironmentVars'

const DEFAULT_MESSAGE_TYPES: MessageType[] = ['BSM', 'MAP', 'SPAT', 'TIM', 'SRM', 'SSM']

const DisplayCounts = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const theme = useTheme()
  const organization = useSelector(selectOrganizationName)
  const countsMsgType = useSelector(selectCountsMsgType)
  const startDate = useSelector(selectCountsStartDate)
  const endDate = useSelector(selectCountsEndDate)

  const [currentSort, setCurrentSort] = React.useState<string | null>(null)

  const maxDurationMs = EnvironmentVars.MAX_QUERY_DURATION_DAYS * 24 * 60 * 60 * 1000
  const queryDurationExceeded = endDate.getTime() - startDate.getTime() > maxDurationMs
  const longRangeWarning =
    !queryDurationExceeded && endDate.getTime() - startDate.getTime() > 86400 * 1000 * 7

  const {
    data: messageCounts,
    isFetching,
    isError,
  } = useGetRsuCountsQuery(
    { organization, startDate, endDate, message: countsMsgType },
    { skip: !organization || queryDurationExceeded }
  )

  const messageTypeOptions = useMemo(() => {
    const configured = EnvironmentVars.getMessageTypes()
    const types = configured.length > 0 ? configured : DEFAULT_MESSAGE_TYPES
    return types.map((type) => ({ value: type, label: type.toUpperCase() }))
  }, [])

  const countList: CountsListElement[] = useMemo(() => {
    return (messageCounts ?? []).map((msgCount) => ({
      key: msgCount.rsu_ip,
      rsu: msgCount.rsu_ip,
      road: msgCount.road || '',
      odeInputCount: msgCount.ode_input_count ?? 0,
      odeOutputCount: msgCount.ode_output_count ?? 0,
    }))
  }, [messageCounts])

  const sortedCountList = useMemo(() => {
    if (!currentSort) return countList

    const key = currentSort.replace('__desc', '') as keyof CountsListElement
    const isDescending = currentSort.includes('__desc')

    return [...countList].sort((a, b) => {
      const aVal = a[key]
      const bVal = b[key]

      if (aVal < bVal) return isDescending ? 1 : -1
      if (aVal > bVal) return isDescending ? -1 : 1
      return 0
    })
  }, [currentSort, countList])

  const sortBy = (key: string) => {
    if (key === currentSort) {
      setCurrentSort(key + '__desc')
    } else {
      setCurrentSort(key)
    }
  }

  const getWarningMessage = () => {
    if (queryDurationExceeded) {
      return (
        <Typography
          component="span"
          role="alert"
          sx={{ backgroundColor: theme.palette.error.main, display: 'flex', justifyContent: 'center', px: 1 }}
        >
          Query duration exceeds the maximum of {EnvironmentVars.MAX_QUERY_DURATION_DAYS} days. Please select a shorter
          time range.
        </Typography>
      )
    }
    if (isError) {
      return (
        <Typography
          component="span"
          role="alert"
          sx={{ backgroundColor: theme.palette.error.main, display: 'flex', justifyContent: 'center', px: 1 }}
        >
          Failed to load message counts from Intersection API.
        </Typography>
      )
    }
    if (longRangeWarning) {
      return (
        <Typography
          component="span"
          role="alert"
          sx={{ backgroundColor: theme.palette.error.main, display: 'flex', justifyContent: 'center', px: 1 }}
        >
          Warning: time ranges greater than 7 days may have longer load times.
        </Typography>
      )
    }
    return null
  }

  const getTable = (loading: boolean, rows: CountsListElement[]) => {
    if (loading) {
      return (
        <div className="table">
          <div className="header">
            <div>RSU</div>
            <div>Road</div>
            <div>Input</div>
            <div>Processed</div>
          </div>
          <span className="bounceLoader">
            <BounceLoader color={theme.palette.primary.main} />
          </span>
        </div>
      )
    }

    return (
      <div className="table">
        <div className="header">
          <div onClick={() => sortBy('rsu')} style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>
            RSU
          </div>
          <div onClick={() => sortBy('road')} style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>
            Road
          </div>
          <div
            onClick={() => sortBy('odeInputCount')}
            style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}
          >
            Input
          </div>
          <div
            onClick={() => sortBy('odeOutputCount')}
            style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}
          >
            Processed
          </div>
        </div>
        <div className="body">{formatRows(rows)}</div>
      </div>
    )
  }

  const formatRows = (rows: CountsListElement[]) => {
    if (rows.length === 0) {
      return (
        <div className="row">
          <div
            style={{
              gridColumn: '1 / span 4',
              textAlign: 'center',
              flex: 4,
            }}
          >
            <Typography>No data found for the selected range</Typography>
          </div>
        </div>
      )
    }
    return rows.map((rowData) => <Row key={rowData.key} {...rowData} />)
  }

  return (
    <Paper sx={{ pb: 1, pl: 1, pr: 1 }}>
      <SideBarHeader
        onClick={() => dispatch(toggleMapMenuSelection('Display Message Counts'))}
        title="Message Counts"
      />
      <Stack direction="column" spacing={2}>
        <Box sx={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
          <LocalizationProvider dateAdapter={AdapterDayjs}>
            <DateTimePicker
              sx={{ width: '90%' }}
              label="Select start date"
              value={dayjs(startDate)}
              maxDateTime={dayjs(endDate)}
              onChange={(e) => {
                if (e && !Number.isNaN(Date.parse(e.toString()))) {
                  dispatch(setCountsStartDate(e.toDate()))
                }
              }}
            />
          </LocalizationProvider>
        </Box>
        <Box sx={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
          <LocalizationProvider dateAdapter={AdapterDayjs}>
            <DateTimePicker
              sx={{ width: '90%' }}
              label="Select end date"
              value={dayjs(endDate)}
              minDateTime={dayjs(startDate)}
              onChange={(e) => {
                if (e && !Number.isNaN(Date.parse(e.toString()))) {
                  dispatch(setCountsEndDate(e.toDate()))
                }
              }}
            />
          </LocalizationProvider>
        </Box>
        <Box sx={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
          <FormControl sx={{ width: '90%' }}>
            <InputLabel htmlFor="counts-msg-dropdown">Message Type</InputLabel>
            <Select
              label="Message Type"
              id="counts-msg-dropdown"
              value={countsMsgType}
              onChange={(event) => dispatch(setCountsMsgType(event.target.value as MessageType))}
              sx={{
                textAlign: 'left',
              }}
            >
              {messageTypeOptions.map((option) => {
                return (
                  <MenuItem value={option.value} key={option.value}>
                    {option.label}
                  </MenuItem>
                )
              })}
            </Select>
          </FormControl>
        </Box>
        {getWarningMessage()}
        {getTable(isFetching, sortedCountList)}
      </Stack>
    </Paper>
  )
}

const Row = ({
  rsu,
  road,
  odeInputCount,
  odeOutputCount,
}: {
  rsu: string
  road: string
  odeInputCount: number
  odeOutputCount: number
}) => {
  const theme = useTheme()
  return (
    <div className="row">
      <div style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>{rsu}</div>
      <div style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>{road}</div>
      <div style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>{odeInputCount}</div>
      <div style={{ borderBottom: `1px solid ${theme.palette.text.primary}` }}>{odeOutputCount}</div>
    </div>
  )
}

export default DisplayCounts
