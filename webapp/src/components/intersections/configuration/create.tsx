import React from 'react'
import { Box, Container, Typography } from '@mui/material'
import { ConfigParamCreateForm } from '../../../features/intersections/configuration/configuration-create-form'
import { selectSelectedIntersectionId } from '../../../generalSlices/intersectionSlice'
import { useParams } from 'react-router-dom'
import { useAppSelector } from '../../../hooks'
import {
  selectParameter,
  selectIntersectionParametersById,
} from '../../../features/api/intersectionConfigParamApiSlice'

const ConfigParamCreate = () => {
  const intersectionId = useAppSelector(selectSelectedIntersectionId)

  const { key } = useParams<{ key: string }>()

  const parameter = useAppSelector(selectParameter(key, intersectionId))

  if (!parameter || intersectionId === -1) {
    return (
      <>
        <Box
          component="main"
          sx={{
            backgroundColor: 'background.default',
            flexGrow: 1,
            py: 8,
          }}
        >
          <Container maxWidth="lg">
            <Box
              sx={{
                alignItems: 'center',
                display: 'flex',
                overflow: 'hidden',
              }}
            >
              <div>
                <Typography variant="h6">
                  Unable to find parameter {key}.
                  <br />
                  Do you have the right intersection ID selected?
                </Typography>
              </div>
            </Box>
          </Container>
        </Box>
      </>
    )
  } else {
    return (
      <>
        <Box
          component="main"
          sx={{
            backgroundColor: 'background.default',
            flexGrow: 1,
            py: 8,
          }}
        >
          <Container maxWidth="md">
            <Box
              sx={{
                alignItems: 'center',
                display: 'flex',
                overflow: 'hidden',
              }}
            >
              <div>
                <Typography noWrap variant="h4">
                  {parameter.key}
                </Typography>
              </div>
            </Box>
            <Box mt={3}>
              <ConfigParamCreateForm parameter={parameter} />
            </Box>
          </Container>
        </Box>
      </>
    )
  }
}

export default ConfigParamCreate
