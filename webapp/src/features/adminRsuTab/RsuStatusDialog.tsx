import React, { useState, useEffect } from 'react'
import { Dialog, DialogTitle, DialogContent, Typography, TextField, IconButton } from '@mui/material'
import {
  ScatterChart,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceArea,
} from 'recharts'
import { Close } from '@mui/icons-material'
import RsuApi, { type RsuState } from '../../apis/intersections/rsu-api'

type RsuStatusDialogProps = {
  open: boolean
  onClose: () => void
  rsuIp: string | null
  token: string
}

const RsuStatusDialog: React.FC<RsuStatusDialogProps> = ({ open, onClose, rsuIp, token }) => {
  const [latestRsuState, setLatestRsuState] = useState<RsuState | null>(null)
  const [historicalData, setHistoricalData] = useState<RsuState[] | null>(null)
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0])

  useEffect(() => {
    if (open && rsuIp) {
      // Query latest RSU status
      RsuApi.getLatestRsuStatus({ token, rsuIp })
        .then((data) => setLatestRsuState(data ?? null))
        .catch(() => setLatestRsuState(null))

      // Query historical RSU data
      const startTime = new Date(selectedDate).setHours(0, 0, 0, 0)
      const endTime = new Date(selectedDate).setHours(23, 59, 59, 999)

      RsuApi.getHistoricalRsuStatus({ token, rsuIp, startTime: new Date(startTime), endTime: new Date(endTime) })
        .then((data) => setHistoricalData(data ?? null))
        .catch(() => setHistoricalData(null))
    }
  }, [open, rsuIp, token, selectedDate])

  const handleDateChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedDate(event.target.value)
  }

  const temperatureData =
    historicalData?.map((data) => ({
      time: data.timestamp,
      value: parseFloat((data.temperature * (9 / 5) + 32).toFixed(1)),
    })) || []

  const uptimeData =
    historicalData?.map((data) => ({
      time: data.timestamp,
      value: data.uptime,
    })) || []

  const temperatureDomain = [
    Math.floor(Math.min(...temperatureData.map((d) => d.value)) / 5) * 5,
    Math.ceil(Math.max(...temperatureData.map((d) => d.value)) / 5) * 5,
  ]

  const uptimeDomain = [
    Math.round(Math.min(...uptimeData.map((d) => d.value)) - 1),
    Math.round(Math.max(...uptimeData.map((d) => d.value)) + 1),
  ]

  const timeDomain = [Math.min(...temperatureData.map((d) => d.time)), Math.max(...temperatureData.map((d) => d.time))]

  // Updated formatTimestamp to include hours and minutes for X-axis
  const formatTimestamp = (unixTime: number) => {
    const date = new Date(unixTime)
    return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${date
      .getMinutes()
      .toString()
      .padStart(2, '0')}` // Format as MM/DD HH:mm
  }

  // Define color gradients for charts
  const redTempTarget = 140
  const yellowTempTarget = 120
  const greenTempTarget = 100

  // Offset for red gradient stop based on temperature domain
  const redOffset = 1 - (redTempTarget - temperatureDomain[0]) / (temperatureDomain[1] - temperatureDomain[0])
  // Offset for yellow gradient stop based on temperature domain
  const yellowOffset = 1 - (yellowTempTarget - temperatureDomain[0]) / (temperatureDomain[1] - temperatureDomain[0])
  // Offset for green gradient stop based on temperature domain
  const greenOffset = 1 - (greenTempTarget - temperatureDomain[0]) / (temperatureDomain[1] - temperatureDomain[0])

  const yellowUptimeTarget = 7776000 // 90 days in seconds
  const greenUptimeTarget = yellowUptimeTarget * 0.9 // 90% of yellow target

  // Offset for green uptime gradient stop
  const greenUptimeOffset = 1 - (greenUptimeTarget - uptimeDomain[0]) / (uptimeDomain[1] - uptimeDomain[0])
  // Offset for yellow uptime gradient stop
  const yellowUptimeOffset = 1 - (yellowUptimeTarget - uptimeDomain[0]) / (uptimeDomain[1] - uptimeDomain[0])

  // Identify reboot points (local minima after a decrease)
  const rebootPoints = uptimeData.reduce((acc, curr, index, arr) => {
    if (index > 0 && arr[index - 1].value > curr.value) {
      acc.push(curr.time) // Identify local minima after a decrease as reboot points
    }
    return acc
  }, [])

  // Calculate reboot zones (from local max to subsequent local min)
  const rebootZones = uptimeData.reduce((zones, curr, index, arr) => {
    if (index > 0 && arr[index - 1].value > curr.value) {
      zones.push({
        start: arr[index - 1].time,
        end: curr.time,
      })
    }
    return zones
  }, [])

  // Formats uptime in days and hours
  const formatUptime = (seconds: number, forTooltip = false) => {
    const days = Math.floor(seconds / 86400)
    const hours = Math.floor((seconds % 86400) / 3600)

    if (days > 0 && hours > 0) {
      return forTooltip ? `${days} days, ${hours} ${hours === 1 ? 'hour' : 'hours'}` : `${days}d ${hours}hr`
    }

    if (days > 0 && hours === 0) {
      return forTooltip ? `${days} days` : `${days}d`
    }

    if (days === 0 && hours > 0) {
      return forTooltip ? `${hours} ${hours === 1 ? 'hour' : 'hours'}` : `${hours}hr`
    }

    return forTooltip ? `0 hours` : `0hr`
  }

  // Updated formatTooltip to include hours and minutes for tooltips
  const formatTooltip = (value: number, name: string, props: any) => {
    if (name === 'Time') {
      const date = new Date(value)
      return [
        `${date.getMonth() + 1}/${date.getDate()}/${date.getFullYear()} ${date.getHours()}:${date
          .getMinutes()
          .toString()
          .padStart(2, '0')}`, // Format as MM/DD/YYYY HH:mm
        name,
      ]
    }
    if (name === 'Temperature') {
      return [`${value}°F`, 'Temperature']
    }
    if (name === 'Uptime') {
      return [formatUptime(value, true), 'Uptime']
    }
    return [value, name]
  }

  // Clear and recalculate reboot points and zones whenever the historical data changes
  useEffect(() => {
    // Clear reboot points and zones when historical data changes
    rebootPoints.splice(0, rebootPoints.length)
    rebootZones.splice(0, rebootZones.length)

    // Recalculate reboot points
    uptimeData.reduce((acc, curr, index, arr) => {
      if (index > 0 && arr[index - 1].value > curr.value) {
        acc.push(curr.time)
      }
      return acc
    }, rebootPoints)

    // Recalculate reboot zones
    uptimeData.reduce((zones, curr, index, arr) => {
      if (index > 0 && arr[index - 1].value > curr.value) {
        zones.push({
          start: arr[index - 1].time,
          end: curr.time,
        })
      }
      return zones
    }, rebootZones)
  }, [historicalData])

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xl" fullWidth>
      <DialogTitle>
        {`Status of RSU at ${rsuIp || 'unknown IP'}`}
        <IconButton onClick={onClose} style={{ position: 'absolute', right: 8, top: 8 }}>
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Typography variant="subtitle2">
          {latestRsuState
            ? `Last status update: ${new Date(latestRsuState.timestamp).toLocaleString()} (${formatUptime(
                Math.floor((Date.now() - latestRsuState.timestamp) / 1000),
                true
              )} ago)`
            : 'No RSU status data.'}
        </Typography>
        <Typography variant="subtitle2">
          {latestRsuState
            ? `Last reboot: ${new Date(
                latestRsuState.timestamp - latestRsuState.uptime * 1000
              ).toLocaleString()} (${formatUptime(
                Math.floor((Date.now() - (latestRsuState.timestamp - latestRsuState.uptime * 1000)) / 1000),
                true
              )} ago)`
            : 'No RSU reboot data.'}
        </Typography>
        <TextField
          label="Select Date"
          type="date"
          value={selectedDate}
          onChange={handleDateChange}
          InputLabelProps={{ shrink: true }}
          fullWidth
          margin="normal"
        />
        <Typography variant="h6">
          Temperature for {`${selectedDate.split('-')[1]}/${selectedDate.split('-')[2]}/${selectedDate.split('-')[0]}`}
        </Typography>
        <ResponsiveContainer width="100%" height={300}>
          <ScatterChart>
            <defs>
              <linearGradient id="temperatureGradient" x1="0" y1="0" x2="0" y2="1">
                {yellowTempTarget < temperatureDomain[1] && <stop offset={`${redOffset}`} stopColor="#ca8282" />}
                <stop offset={`${yellowOffset}`} stopColor="#f0e68c" />
                {yellowTempTarget > temperatureDomain[0] && <stop offset={`${greenOffset}`} stopColor="#82ca9d" />}
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" domain={timeDomain} name="Time" tickFormatter={formatTimestamp} type="number" />
            <YAxis
              dataKey="value"
              domain={temperatureDomain}
              name="Temperature"
              label={{ value: 'Temperature (°F)', angle: -90, position: 'center', dx: -20 }}
              ticks={Array.from(
                { length: (temperatureDomain[1] - temperatureDomain[0]) / 5 + 1 },
                (_, i) => temperatureDomain[0] + i * 5
              )}
            />
            <Tooltip isAnimationActive={false} formatter={formatTooltip} />
            <Scatter
              data={temperatureData}
              line={{ stroke: 'url(#temperatureGradient)', strokeWidth: 3 }}
              fill="rgba(0, 0, 0, 0)"
              lineJointType="monotoneX"
            />
          </ScatterChart>
        </ResponsiveContainer>
        <Typography variant="h6">
          Uptime for {`${selectedDate.split('-')[1]}/${selectedDate.split('-')[2]}/${selectedDate.split('-')[0]}`}
        </Typography>
        <ResponsiveContainer width="100%" height={300}>
          <ScatterChart>
            <defs>
              <linearGradient id="uptimeGradient" x1="0" y1="0" x2="0" y2="1">
                {yellowUptimeTarget <= uptimeDomain[1] && <stop offset={`${yellowUptimeOffset}`} stopColor="#f0e68c" />}
                <stop offset={`${greenUptimeOffset}`} stopColor="#82ca9d" />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" domain={timeDomain} name="Time" tickFormatter={formatTimestamp} type="number" />
            <YAxis
              dataKey="value"
              domain={uptimeDomain}
              name="Uptime"
              label={{ value: 'Uptime', angle: -90, position: 'center', dx: -40 }}
              tickFormatter={(value) => formatUptime(value)}
              ticks={Array.from(
                { length: (uptimeDomain[1] - uptimeDomain[0]) / 5 + 1 },
                (_, i) => uptimeDomain[0] + i * 5
              )}
              width={80}
            />
            <Tooltip isAnimationActive={false} formatter={formatTooltip} />
            {/* Render shaded areas for reboot zones with labels starting at the right end */}
            {rebootZones.map((zone, index) => (
              <ReferenceArea
                key={index}
                x1={zone.start}
                x2={zone.end}
                stroke="red"
                strokeOpacity={0.3}
                fill="red"
                fillOpacity={0.1}
                label={{
                  value: 'Offline',
                  position: 'insideTopRight',
                  fill: 'red',
                  fontSize: 14,
                  angle: 90,
                  dy: 35,
                  dx: 20,
                }}
              />
            ))}
            <Scatter
              data={uptimeData}
              line={{ stroke: 'url(#uptimeGradient)', strokeWidth: 3 }}
              fill="rgba(0, 0, 0, 0)"
              lineJointType="monotoneX"
            />
          </ScatterChart>
        </ResponsiveContainer>
      </DialogContent>
    </Dialog>
  )
}

export default RsuStatusDialog
