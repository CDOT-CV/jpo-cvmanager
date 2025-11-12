import { Box, Checkbox, Fab, IconButton, Paper, Typography, useTheme } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { selectLayersVisible, MAP_LAYERS, setLayerVisibility } from './map-slice'
import React from 'react'
import { Close, Layers } from '@mui/icons-material'

type LayerMenuProps = {
  openPanel: string
  setOpenPanel: (panel: string) => void
}

const layerTitleMap: { [label: string]: MAP_LAYERS[] } = {
  'Map Lanes': ['map-message'],
  'Map Lane Labels': ['map-message-labels'],
  'Connecting Lanes': ['connecting-lanes'],
  'Connecting Lane Labels': ['connecting-lanes-labels'],
  'Invalid Lane Collections': ['invalid-lane-collection'],
  'Signal States': ['signal-states'],
  BSMs: ['bsm'],
  SRMs: ['srm'],
  'SSM Connection Status': ['ssm-connection-status'],
  'SSM Highlighted Lanes': ['ssm-connection-highlight', 'srm-requested-lanes'],
}

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

  const layerRow = (label: string, ids: MAP_LAYERS[]) => {
    return (
      <Box
        key={label}
        sx={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <Checkbox
          onChange={(event) => {
            ids.forEach((id) => {
              dispatch(setLayerVisibility({ key: id, visible: event.target.checked }))
            })
          }}
          checked={ids.every((id) => layersVisible[id])}
        />
        <Typography fontSize="16px">{label}</Typography>
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
                  <Box>{Object.entries(layerTitleMap).map(([label, ids]) => layerRow(label, ids))}</Box>
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
