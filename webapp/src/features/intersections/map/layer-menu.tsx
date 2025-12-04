import { Box, Checkbox, Fab, IconButton, Paper, Typography, useTheme, Tooltip } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { selectLayersVisible, MAP_LAYERS, setLayerVisibility } from './map-slice'
import { LAYER_RENDER_ORDER } from './map-layer-style-slice'
import React from 'react'
import { Close, Layers, Label } from '@mui/icons-material'

type LayerMenuProps = {
  openPanel: string
  setOpenPanel: (panel: string) => void
}

type LayerConfig = {
  label: string
  layerId: MAP_LAYERS
  labelLayerId?: MAP_LAYERS
}

// Layer configurations - order matches LAYER_RENDER_ORDER from map-layer-style-slice
const layerConfigs: LayerConfig[] = [
  { label: 'SRM Requested Lanes', layerId: 'srm-requested-lanes' },
  { label: 'SSM Highlighted Lanes', layerId: 'ssm-connection-highlight' },
  { label: 'Map Lanes', layerId: 'map-message', labelLayerId: 'map-message-labels' },
  { label: 'Connecting Lanes', layerId: 'connecting-lanes', labelLayerId: 'connecting-lanes-labels' },
  { label: 'Invalid Lane Collections', layerId: 'invalid-lane-collection' },
  { label: 'Signal States', layerId: 'signal-states' },
  { label: 'SSM Connection Status', layerId: 'ssm-connection-status' },
  { label: 'BSMs', layerId: 'bsm' },
  { label: 'SRMs', layerId: 'srm' },
]

function LayerMenu(props: LayerMenuProps) {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const layersVisible = useSelector(selectLayersVisible)

  const theme = useTheme()

  const toggleOpen = () => {
    if (props.openPanel === 'layer-menu') {
      props.setOpenPanel('')
    } else {
      props.setOpenPanel('layer-menu')
    }
  }

  const layerRow = (config: LayerConfig, index: number) => {
    const { label, layerId, labelLayerId } = config
    const isLayerVisible = layersVisible[layerId]
    const isLabelVisible = labelLayerId ? layersVisible[labelLayerId] : undefined

    // Calculate z-order based on position in LAYER_RENDER_ORDER
    const zOrder = LAYER_RENDER_ORDER.indexOf(layerId as any) + 1

    return (
      <Box
        key={layerId}
        sx={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          gap: 1,
          justifyContent: 'space-between',
          '&:hover': {
            backgroundColor: theme.palette.action.hover,
          },
          px: 1,
          py: 0.5,
          borderRadius: 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flex: 1 }}>
          <Checkbox
            onChange={(event) => {
              dispatch(setLayerVisibility({ key: layerId, visible: event.target.checked }))
            }}
            checked={isLayerVisible}
            size="small"
          />
          <Typography fontSize="16px">{label}</Typography>
        </Box>
        {labelLayerId && (
          <Tooltip title={isLabelVisible ? 'Hide Labels' : 'Show Labels'}>
            <IconButton
              size="small"
              onClick={() => {
                dispatch(setLayerVisibility({ key: labelLayerId, visible: !isLabelVisible }))
              }}
              sx={{
                color: isLabelVisible ? theme.palette.primary.main : theme.palette.action.disabled,
                '&:hover': {
                  backgroundColor: theme.palette.action.hover,
                },
              }}
            >
              <Label fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    )
  }

  return (
    <>
      <Fab
        size="small"
        onClick={() => {
          toggleOpen()
        }}
        sx={{
          position: 'absolute',
          zIndex: 10,
          top: theme.spacing(3),
          right: theme.spacing(24),
          backgroundColor: theme.palette.background.paper,
          '&:hover': {
            backgroundColor: theme.palette.custom.intersectionMapButtonHover,
          },
        }}
      >
        <Layers />
      </Fab>
      <div
        style={{
          position: 'absolute',
          zIndex: 10,
          bottom: theme.spacing(3),
          maxHeight: 'calc(100vh - 240px)',
          right: 0,
          width: props.openPanel === 'layer-menu' ? 300 : 0,
          fontSize: '16px',
          overflow: 'auto',
          scrollBehavior: 'auto',
          borderRadius: '4px',
        }}
      >
        <Box style={{ position: 'relative', height: '100%', width: '100%' }}>
          <Paper sx={{ height: '100%', width: '100%', px: 2, pb: 2 }} square>
            <Box>
              {props.openPanel !== 'layer-menu' ? null : (
                <>
                  <Box
                    sx={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '8px 16px',
                    }}
                  >
                    <Typography fontSize="16px">Map Layers</Typography>
                    <IconButton
                      onClick={() => {
                        toggleOpen()
                      }}
                    >
                      <Close color="info" />
                    </IconButton>
                  </Box>
                  <Box>{layerConfigs.map((config, index) => layerRow(config, index))}</Box>
                </>
              )}
            </Box>
          </Paper>
        </Box>
      </div>
    </>
  )
}

export default LayerMenu
