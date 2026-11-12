import { ReactNode } from 'react'
import { Box, Tab, Tabs, useTheme } from '@mui/material'
import { Link, Navigate, Route, Routes, useParams } from 'react-router-dom'
import AdminObuFirmwareTab from '../adminObuFirmwareTab/AdminObuFirmwareTab'
import AdminFirmwareTab from './AdminFirmwareTab'

type FirmwareSubTab = {
  path: string
  label: string
  element: ReactNode
}

// RSU and OBU firmware come from separate paged APIs with different columns and
// actions, so each keeps its own table under a shared Firmware tab
const firmwareSubTabs: FirmwareSubTab[] = [
  { path: 'rsu', label: 'RSU Firmware', element: <AdminFirmwareTab /> },
  { path: 'obu', label: 'OBU Firmware', element: <AdminObuFirmwareTab /> },
]
const DEFAULT_SUB_TAB = firmwareSubTabs[0].path

const FirmwareTabs = () => {
  const theme = useTheme()
  // The admin tab route is firmware/*, so the splat names the selected sub-tab
  const subPath = useParams()['*']?.split('/')[0]
  const selected = firmwareSubTabs.some((tab) => tab.path === subPath) ? subPath : DEFAULT_SUB_TAB

  return (
    <Box
      sx={{
        // Leave room for the sub-tab bar above each table's scrolling area
        '& .scroll-div-tab': { height: 'calc(100vh - 218px)' },
      }}
    >
      <Tabs
        value={selected}
        aria-label="Firmware type"
        sx={{
          mb: 1,
          '& .MuiButtonBase-root': { textTransform: 'capitalize' },
          '& .MuiTabs-indicator': { backgroundColor: theme.palette.custom.rowActionIcon },
          '& .Mui-selected': { color: `${theme.palette.custom.rowActionIcon} !important` },
        }}
      >
        {firmwareSubTabs.map((tab) => (
          <Tab key={tab.path} label={tab.label} value={tab.path} component={Link} to={tab.path} />
        ))}
      </Tabs>
      <Routes>
        {firmwareSubTabs.map((tab) => (
          <Route key={tab.path} path={`${tab.path}/*`} element={tab.element} />
        ))}
        <Route path="*" element={<Navigate to={DEFAULT_SUB_TAB} replace />} />
      </Routes>
    </Box>
  )
}

export default FirmwareTabs
