import { Paper, Box, IconButton, Typography, Fab, AccordionSummary, AccordionDetails } from '@mui/material'
import React from 'react'
import MuiAccordion, { AccordionProps } from '@mui/material/Accordion'
import { styled, useTheme } from '@mui/material/styles'
import { selectMapLegendColors } from './map-layer-style-slice'
import { useSelector } from 'react-redux'
import { Close, ExpandMoreOutlined, FormatListBulleted } from '@mui/icons-material'

const Accordion = styled((props: AccordionProps) => <MuiAccordion disableGutters elevation={0} square {...props} />)(
  () => ({})
)

type MapLegendProps = {
  openPanel: string
  setOpenPanel: (panel: string) => void
}

const hexToFilter = (hex: string): string => {
  // Simulate mapbox sdf recoloring using CSS filters
  // Remove # if present
  hex = hex.replace('#', '')

  // Convert hex to RGB (0-255)
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)

  // Check if it's a grey color (R ≈ G ≈ B)
  const isGrey = Math.abs(r - g) < 5 && Math.abs(g - b) < 5 && Math.abs(r - b) < 5

  if (isGrey) {
    // For grey colors, just use brightness
    const brightness = r / 255
    return `brightness(0) saturate(0%) invert(${brightness * 100}%)`
  }

  // For colored icons
  const rNorm = r / 255
  const gNorm = g / 255
  const bNorm = b / 255

  const max = Math.max(rNorm, gNorm, bNorm)
  const min = Math.min(rNorm, gNorm, bNorm)
  const delta = max - min

  // Calculate hue
  let hue = 0
  if (delta !== 0) {
    if (max === rNorm) {
      hue = 60 * (((gNorm - bNorm) / delta) % 6)
    } else if (max === gNorm) {
      hue = 60 * ((bNorm - rNorm) / delta + 2)
    } else {
      hue = 60 * ((rNorm - gNorm) / delta + 4)
    }
  }
  if (hue < 0) hue += 360

  // Calculate saturation
  const saturation = max === 0 ? 0 : delta / max

  // Calculate lightness (for better color accuracy)
  const lightness = (max + min) / 2

  // Adjust saturation multiplier based on the color
  // Pure colors (high saturation) need less boost
  const saturationMultiplier = saturation > 0.9 ? 5000 : 10000

  // For very bright, saturated colors (like pure red), reduce brightness boost
  const brightnessMultiplier = saturation > 0.9 && max > 0.9 ? 0.9 : 1.0

  return `brightness(0) saturate(100%) invert(${lightness * 100}%) sepia(100%) saturate(${
    saturation * saturationMultiplier
  }%) hue-rotate(${hue}deg) brightness(${brightnessMultiplier * 100}%)`
}

export const MapLegend = (props: MapLegendProps) => {
  const mapLegendColors = useSelector(selectMapLegendColors)
  const theme = useTheme()

  const toggleOpen = () => {
    if (props.openPanel === 'map-legend') {
      props.setOpenPanel('')
    } else {
      props.setOpenPanel('map-legend')
    }
  }

  const { bsmColors, travelConnectionColors, laneColors, signalHeadIcons, ssmStatusIcons, other } = mapLegendColors

  const bsmColorsList: JSX.Element[] = []
  for (const [key, value] of Object.entries(bsmColors)) {
    bsmColorsList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <div style={{ height: 20, width: 20, backgroundColor: value as string, borderRadius: '50%' }} />
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key.toLowerCase()}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  const travelConnectionColorsList: JSX.Element[] = []
  for (const [key, value] of Object.entries(travelConnectionColors)) {
    const barHeight = 20
    const numColorSets = 2
    const heightFactor = barHeight / numColorSets / value[1].reduce((partialSum, a) => partialSum + a, 0)
    const heightColored = value[1][0] * heightFactor
    const heightWhite = (value[1].length == 1 ? 0 : value[1][1]) * heightFactor
    travelConnectionColorsList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <div style={{ height: heightColored, width: 10, backgroundColor: value[0] }} />
            <div style={{ height: heightWhite, width: 10, backgroundColor: '#ffffff' }} />
            <div style={{ height: heightColored, width: 10, backgroundColor: value[0] }} />
            <div style={{ height: heightWhite, width: 10, backgroundColor: '#ffffff' }} />
          </div>
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key.toLowerCase()}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  const laneColorsList: JSX.Element[] = []
  for (const [key, value] of Object.entries(laneColors)) {
    laneColorsList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <div style={{ height: 20, width: 20, backgroundColor: value as string }} />
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key.toLowerCase()}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  const signalHeadIconsList: JSX.Element[] = []
  for (const [key, value] of Object.entries(signalHeadIcons)) {
    signalHeadIconsList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <img src={value as string} style={{ height: 40, width: 30 }} />
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key.toLowerCase()}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  const ssmStatusIconList: JSX.Element[] = []
  for (const [key, value] of Object.entries(ssmStatusIcons)) {
    ssmStatusIconList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              padding: '2px',
              borderRadius: '2px',
              display: 'inline-flex', // ⚡ Add this to shrink-wrap
              alignItems: 'center', // ⚡ Add this to center the icon
              justifyContent: 'center', // ⚡ Add this to center the icon
            }}
          >
            <img
              src={value[0] as string}
              style={{
                width: 20,
                height: 20,
                filter: hexToFilter(value[1]),
              }}
            />
          </div>
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key.toLowerCase()}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  const otherLayerList: JSX.Element[] = []
  for (const [key, value] of Object.entries(other)) {
    otherLayerList.push(
      <React.Fragment key={key}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px',
          }}
        >
          <div style={{ height: 20, width: 20, backgroundColor: value as string }} />
          <Typography fontSize="14px" sx={{ ml: 1, textTransform: 'capitalize' }}>
            {key}
          </Typography>
        </div>
      </React.Fragment>
    )
  }

  return (
    <>
      <Fab
        id="map-legend-button"
        sx={{
          position: 'absolute',
          zIndex: 10,
          top: theme.spacing(3),
          right: theme.spacing(10),
          backgroundColor: theme.palette.background.paper,
          '&:hover': {
            backgroundColor: theme.palette.custom.intersectionMapButtonHover,
          },
        }}
        size="small"
        onClick={() => {
          toggleOpen()
        }}
      >
        <FormatListBulleted />
      </Fab>
      <div
        style={{
          position: 'absolute',
          zIndex: 10,
          bottom: theme.spacing(3),
          maxHeight: 'calc(100vh - 240px)',
          right: 0,
          width: props.openPanel === 'map-legend' ? 600 : 0,
          fontSize: '16px',
        }}
      >
        {props.openPanel !== 'map-legend' ? null : (
          <Box style={{ position: 'relative', height: '100%', width: '100%' }}>
            <Paper sx={{ height: '100%', width: '100%', borderRadius: '4px' }} id="legend-paper" square>
              <Box>
                <Box
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '8px 16px',
                  }}
                >
                  <Typography fontSize="16px">Legend</Typography>
                  <IconButton
                    onClick={() => {
                      toggleOpen()
                    }}
                  >
                    <Close color="info" />
                  </IconButton>
                </Box>
                <Box
                  sx={{
                    maxHeight: '600px',
                    overflow: 'auto',
                    scrollbarColor: `${theme.palette.text.primary} ${theme.palette.background.paper}`,
                    borderRadius: '4px',
                  }}
                >
                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">Signal Heads</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {signalHeadIconsList}
                      </div>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">Lane Lines</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {laneColorsList}
                      </div>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">Lane Connections</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {travelConnectionColorsList}
                      </div>
                    </AccordionDetails>
                  </Accordion>
                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">SSM Status Icons</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {ssmStatusIconList}
                      </div>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">BSM Colors</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {bsmColorsList}
                      </div>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion
                    sx={{
                      '& .Mui-expanded': {
                        backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
                      },
                    }}
                    disableGutters
                  >
                    <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
                      <Typography fontSize="16px">Other Layers</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          overflowY: 'auto',
                          justifyContent: 'flex-start',
                        }}
                      >
                        {otherLayerList}
                      </div>
                    </AccordionDetails>
                  </Accordion>
                </Box>
              </Box>
            </Paper>
          </Box>
        )}
      </div>
    </>
  )
}
