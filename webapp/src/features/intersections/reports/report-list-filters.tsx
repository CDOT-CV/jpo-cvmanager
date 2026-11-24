import { Box, Button, CircularProgress, Typography } from '@mui/material'
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs'
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import dayjs from 'dayjs'
import PropTypes from 'prop-types'
import React, { useState } from 'react'

export type ReportListFilter = {
  startDate: Date | undefined
  endDate: Date | undefined
}

interface ReportListFiltersProps {
  filters: ReportListFilter
  onChange: (filters: ReportListFilter) => void
  loading: boolean
  containerRef: React.RefObject<Element>
  setOpenReportGenerationDialog: (open: boolean) => void
}

const getFilterError = ({ startDate, endDate }: ReportListFilter): string => {
  if (startDate === undefined || endDate === undefined) {
    return 'Start Date and End Date must be set'
  }
  if (startDate >= endDate) {
    return 'Start Date must be before End Date'
  }
  return ''
}

export const ReportListFilters = (props: ReportListFiltersProps) => {
  const { filters, onChange, loading, setOpenReportGenerationDialog } = props
  const [draft, setDraft] = useState<{ source: ReportListFilter; value: ReportListFilter } | null>(null)
  // Discard local edits when the parent supplies a new filter value.
  const currentFilters = draft?.source === filters ? draft.value : filters
  const filterError = getFilterError(currentFilters)

  const startDateChange = (date: Date | undefined) => {
    handleUpdatedFilters({ ...currentFilters, startDate: date })
  }

  const endDateChange = (date: Date | undefined) => {
    handleUpdatedFilters({ ...currentFilters, endDate: date })
  }

  const handleUpdatedFilters = (updatedFilters: ReportListFilter) => {
    setDraft({ source: filters, value: updatedFilters })
    if (!getFilterError(updatedFilters)) {
      onChange(updatedFilters)
    }
  }

  return (
    <Box
      sx={{
        px: 3,
      }}
    >
      <Box sx={{ my: 2 }}>
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <DateTimePicker
            label="From"
            onChange={(e) => startDateChange(e?.toDate())}
            value={dayjs(currentFilters.startDate)}
            disabled={loading}
          />
          <DateTimePicker
            label="To"
            onChange={(e) => endDateChange(e?.toDate())}
            value={dayjs(currentFilters.endDate)}
            disabled={loading}
            sx={{
              ml: 3,
            }}
          />
        </LocalizationProvider>
      </Box>
      {Boolean(filterError) && (
        <Typography color="red" sx={{ mt: 3 }} variant="subtitle2">
          Invalid filters: {filterError}
        </Typography>
      )}
      <Box sx={{ m: 1, position: 'relative', display: 'flex', alignItems: 'center' }}>
        {loading && (
          <CircularProgress
            size={24}
            sx={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              marginTop: '-12px',
              marginLeft: '-12px',
            }}
          />
        )}
      </Box>
      <Button
        component="a"
        variant="contained"
        onClick={() => {
          setOpenReportGenerationDialog(true)
        }}
        className="museo-slab capital-case"
      >
        Generate Manual Report
      </Button>
    </Box>
  )
}

ReportListFilters.propTypes = {
  containerRef: PropTypes.any,
  filters: PropTypes.object,
  onChange: PropTypes.func,
  onClose: PropTypes.func,
  open: PropTypes.bool,
  loading: PropTypes.bool,
}
