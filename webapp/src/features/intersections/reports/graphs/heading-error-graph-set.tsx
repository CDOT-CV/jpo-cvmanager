import React from 'react'
import { Box, Typography } from '@mui/material'
import HeadingErrorOverTimeGraph from './heading-error-over-time-graph'
import { LaneDirectionOfTravelReportDataByLaneId } from '../../../../models/ReportData'

interface HeadingErrorGraphSetProps {
  data: LaneDirectionOfTravelReportDataByLaneId
  headingTolerance: number
}

const HeadingErrorGraphSet: React.FC<HeadingErrorGraphSetProps> = ({ data, headingTolerance }) => {
  return (
    <Box>
      {Object.keys(data).length === 0 ? (
        <Typography variant="subtitle1" align="center" sx={{ mt: 2, color: (theme) => theme.palette.grey[700] }}>
          - No events for this time period -
        </Typography>
      ) : (
        Object.entries(data).map(([laneID, ldotReportData]) => (
          <Box key={laneID} id={`heading-error-graph-${laneID}`} sx={{ mb: 6 }}>
            <HeadingErrorOverTimeGraph
              data={ldotReportData}
              laneNumber={laneID.toString()}
              headingTolerance={headingTolerance}
            />
          </Box>
        ))
      )}
    </Box>
  )
}

export default HeadingErrorGraphSet
