import React, { useState, useEffect, useMemo, ChangeEvent } from 'react'
import { Typography, Accordion, AccordionDetails, AccordionSummary, Paper, Checkbox } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { MAP_LAYERS, selectLayersVisible, setLayerVisibility } from './map-slice'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { ExpandMoreOutlined, Key } from '@mui/icons-material'

function LayerMenu() {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const layersVisible = useSelector(selectLayersVisible)

  const theme = useTheme()

  const layerRow = (id: MAP_LAYERS) => {
    return (
      <div>
        <Checkbox
          onChange={(event) => {
            setLayerVisibility({key: id, visible: event.target.checked})
          }}
          checked={layersVisible[id]}
        />
        <Typography fontSize="16px">Map Lanes</Typography>
      </div>
    )
  }

  return (
    <Paper
      sx={{
        maxHeight: '600px',
        overflow: 'auto',
        scrollbarColor: `${theme.palette.text.primary} ${theme.palette.background.paper}`,
      }}
    >
      <Accordion
        disableGutters
        sx={{
          py: 0.5,
          borderRadius: '4px',
          '& .Mui-expanded': {
            backgroundColor: theme.palette.custom.intersectionMapAccordionExpanded,
          },
        }}
      >
        <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
          <Typography fontSize="16px">Map Lanes</Typography>
        </AccordionSummary>
        <AccordionDetails>
            {Object.keys(layersVisible).map((key: MAP_LAYERS) => layerRow(key))}
        </AccordionDetails>
      </Accordion>
    </Paper>
  )
}

export default LayerMenu
