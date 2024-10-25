import React, { ReactElement, useEffect, useState } from 'react'
import { useDispatch } from 'react-redux'
import { updateTableData as updateRsuTableData } from '../features/adminRsuTab/adminRsuTabSlice'
import { getAvailableUsers } from '../features/adminUserTab/adminUserTabSlice'

import '../features/adminRsuTab/Admin.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../store'
import { Box, IconButton, Tab, Tabs, Typography } from '@mui/material'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { NotFound } from '../pages/404'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'

interface TabPanelProps {
  children?: React.ReactNode
}

function TabPanel(props: TabPanelProps) {
  const { children, ...other } = props

  return (
    <div
      role="tabpanel"
      id={`vertical-tabpanel`}
      aria-labelledby={`vertical-tab`}
      style={{ width: '100%', overflowY: 'auto' }}
      {...other}
    >
      <Box sx={{ p: 3 }}>
        <Typography>{children}</Typography>
      </Box>
    </div>
  )
}

interface VerticalTabItem {
  path: string
  title: string
  icon?: ReactElement
  adminRequired?: boolean
  child: React.ReactNode
}

interface VerticalTabProps {
  notFoundRoute: React.ReactNode
  defaultTabIndex?: number
  tabs: VerticalTabItem[]
  iconOnly?: boolean
}

function VerticalTabs(props: VerticalTabProps) {
  const { notFoundRoute, defaultTabIndex, tabs } = props
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const location = useLocation()
  const defaultTabKey = tabs[defaultTabIndex ?? 0]?.path

  const tabKeys = tabs.map((tab) => tab.path)
  const pathSegments = location.pathname.split('/')
  const getSelectedTab = () => tabKeys.find((key) => pathSegments.includes(key)) ?? defaultTabKey

  const [value, setValue] = useState<string | number>(getSelectedTab())
  const [menuOpen, setMenuOpen] = React.useState(false)

  useEffect(() => {
    setValue(getSelectedTab())
  }, [location.pathname])

  const handleChange = (_e, newValue) => {
    setValue(newValue)
  }

  useEffect(() => {
    dispatch(updateRsuTableData())
    dispatch(getAvailableUsers())
  }, [dispatch])

  return (
    <Box
      sx={{
        flexGrow: 1,
        bgcolor: 'background.default',
        display: 'flex',
        width: '100%',
        height: 'calc(100% - 135px)',
      }}
    >
      <Box
        sx={{
          bgcolor: 'background.paper',
        }}
      >
        <IconButton onClick={() => setMenuOpen(!menuOpen)} style={{ marginLeft: menuOpen ? 120 : 20 }}>
          {menuOpen ? <ChevronLeftIcon /> : <ChevronRightIcon />}
        </IconButton>
        <Tabs
          value={value}
          onChange={handleChange}
          aria-label="Navigation"
          indicatorColor="primary"
          textColor="inherit"
          orientation="vertical"
          sx={{ width: menuOpen ? 170 : 60 }}
          TabIndicatorProps={{
            style: {
              right: 'auto', // remove the default right positioning
              left: 0, // add left positioning
              width: 4, // width of the indicator
            },
          }}
        >
          {tabs.map((tab) => {
            const index = tabs.indexOf(tab)
            return (
              <Tab
                label={menuOpen ? tab.title : null}
                value={tab.path}
                component={Link}
                to={tab.path}
                icon={tab.icon}
                iconPosition="start"
                sx={{
                  backgroundColor: value === tab.path || value === index ? '#0e2052' : 'transparent',
                  fontSize: 20,
                  height: '70px',
                  left: 0,
                  alignItems: 'center',
                  justifyContent: 'flex-start', // Align contents to the left

                  textTransform: 'none',
                  '&&': { color: value === tab.path || value === index ? '#fff' : '#d4d4d4' },
                }}
              />
            )
          })}
        </Tabs>
      </Box>
      <TabPanel>
        <Routes>
          <Route index element={<Navigate to={tabs[defaultTabIndex ?? 0]?.path} replace />} />
          {tabs.map((tab) => (
            <Route key={tab.path} path={`${tab.path}/*`} element={tab.child} />
          ))}
          <Route path="*" element={notFoundRoute} />
        </Routes>
      </TabPanel>
    </Box>
  )
}

export default VerticalTabs
