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

  const layerRow = (id: MAP_LAYERS) => {
    return (
      <div>
        <Checkbox
          onChange={(event) => {
            dispatch(setLayerVisibility({ key: id, visible: event.target.checked }))
          }}
          checked={layersVisible[id]}
        />
        <Typography fontSize="16px">Map Lanes</Typography>
      </div>
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
          right: theme.spacing(17),
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
          width: props.openPanel === 'visual-settings' ? 600 : 0,
          fontSize: '16px',
          overflow: 'auto',
          scrollBehavior: 'auto',
          borderRadius: '4px',
        }}
      >
        <Box style={{ position: 'relative', height: '100%', width: '100%' }}>
          <Paper sx={{ height: '100%', width: '100%', px: 2, pb: 2 }} square>
            <Box>
              {props.openPanel !== 'visual-settings' ? null : (
                <>
                  <Box
                    sx={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '8px 16px',
                    }}
                  >
                    <Typography fontSize="16px">Visual Settings</Typography>
                    <IconButton
                      onClick={() => {
                        toggleOpen()
                      }}
                    >
                      <Close color="info" />
                    </IconButton>
                  </Box>
                  <div>{Object.keys(layersVisible).map((key: MAP_LAYERS) => layerRow(key))}</div>
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
