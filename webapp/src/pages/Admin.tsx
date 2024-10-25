import React, { useEffect } from 'react'
import { useDispatch } from 'react-redux'
import { updateTableData as updateRsuTableData } from '../features/adminRsuTab/adminRsuTabSlice'
import { getAvailableUsers } from '../features/adminUserTab/adminUserTabSlice'
import '../features/adminRsuTab/Admin.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../store'
import AdminOrganizationTab from '../features/adminOrganizationTab/AdminOrganizationTab'
import AdminRsuTab from '../features/adminRsuTab/AdminRsuTab'
import AdminUserTab from '../features/adminUserTab/AdminUserTab'
import { NotFound } from './404'
import { SecureStorageManager } from '../managers'
import { getUserNotifications } from '../features/adminNotificationTab/adminNotificationTabSlice'
import VerticalTabs from '../components/VerticalTabs'
import { Menu, Tab, Tabs } from '@mui/material'
import { Help } from '@mui/icons-material'
import { Routes, Route, Navigate } from 'react-router-dom'
import AdminNotificationTab from '../features/adminNotificationTab/AdminNotificationTab'
import IntersectionDashboard from './IntersectionDashboard'
import IntersectionMapView from './IntersectionMapView'

function samePageLinkNavigation(event: React.MouseEvent<HTMLAnchorElement, MouseEvent>) {
  if (
    event.defaultPrevented ||
    event.button !== 0 || // ignore everything but left-click
    event.metaKey ||
    event.ctrlKey ||
    event.altKey ||
    event.shiftKey
  ) {
    return false
  }
  return true
}

interface LinkTabProps {
  label?: string
  href?: string
  selected?: boolean
}

function LinkTab(props: LinkTabProps) {
  return (
    <Tab
      component="a"
      onClick={(event: React.MouseEvent<HTMLAnchorElement, MouseEvent>) => {
        // Routing libraries handle this, you can remove the onClick handle when using them.
        if (samePageLinkNavigation(event)) {
          event.preventDefault()
        }
      }}
      aria-current={props.selected ? 'page' : undefined}
      {...props}
    />
  )
}

function Admin() {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const [value, setValue] = React.useState(0)

  const handleChange = (event: React.SyntheticEvent, newValue: number) => {
    // event.type can be equal to focus with selectionFollowsFocus.
    if (
      event.type !== 'click' ||
      (event.type === 'click' && samePageLinkNavigation(event as React.MouseEvent<HTMLAnchorElement, MouseEvent>))
    ) {
      setValue(newValue)
    }
  }

  useEffect(() => {
    dispatch(updateRsuTableData())
    dispatch(getAvailableUsers())
    dispatch(getUserNotifications())
  }, [dispatch])

  return (
    <>
      {SecureStorageManager.getUserRole() !== 'admin' ? (
        <div id="admin">
          <NotFound description="You do not have permission to view this page. Please return to main dashboard: " />
        </div>
      ) : (
        <div id="admin" style={{ flexGrow: 1 }}>
          <h2 className="adminHeader" style={{ paddingTop: 0, paddingBottom: 0 }}>
            CV Manager Admin Interface
          </h2>
          <Tabs value={value} onChange={handleChange} aria-label="nav tabs example" role="navigation">
            <LinkTab label="RSUs" href="/rsus" />
            <LinkTab label="Users" href="/users" />
            <LinkTab label="Organizations" href="/organizations" />
          </Tabs>
          <div>
            <Routes>
              <Route index element={<Navigate to="rsus" replace />} />
              <Route path="rsus/*" element={<AdminRsuTab />} />
              <Route path="users/*" element={<AdminUserTab />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </div>
        </div>
      )}
    </>
  )
}

export default Admin
