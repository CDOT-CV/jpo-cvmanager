import React, { useEffect } from 'react'
import { css } from '@emotion/react'
import RingLoader from 'react-spinners/RingLoader'
import Header from './components/Header'
import Menu from './features/menu/Menu'
import Help from './components/Help'
import Admin from './pages/Admin'
import Grid2 from '@mui/material/Grid2'
import Tabs, { TabItem } from './components/Tabs'
import Map from './pages/Map'
import './App.css'
import { useSelector, useDispatch } from 'react-redux'
import {
  // Actions
  getRsuData,
} from './generalSlices/rsuSlice'
import { selectAuthLoginData, selectLoadingGlobal, selectOrganizationName } from './generalSlices/userSlice'
import { SecureStorageManager } from './managers'
import { ReactKeycloakProvider } from '@react-keycloak/web'
import keycloak from './keycloak-config'
import { keycloakLogin } from './generalSlices/userSlice'
import { ThunkDispatch } from 'redux-thunk'
import { RootState } from './store'
import { AnyAction } from '@reduxjs/toolkit'
import { Routes, Route, Navigate } from 'react-router-dom'
import IntersectionMapView from './pages/IntersectionMapView'
import IntersectionDashboard from './pages/IntersectionDashboard'
import { NotFound } from './pages/404'
import AdminNotificationTab from './features/adminNotificationTab/AdminNotificationTab'
import VerticalTabs from './components/VerticalTabs'
import MapIcon from '@mui/icons-material/Map'
import TrafficIcon from '@mui/icons-material/Traffic'
import DashboardIcon from '@mui/icons-material/Dashboard'
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts'
import HelpIcon from '@mui/icons-material/Help'
import SettingsIcon from '@mui/icons-material/Settings'

let loginDispatched = false

const Dashboard = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const authLoginData = useSelector(selectAuthLoginData)
  const loadingGlobal = useSelector(selectLoadingGlobal)
  const organizationName = useSelector(selectOrganizationName)

  useEffect(() => {
    keycloak
      .updateToken(300)
      .then(function (refreshed: boolean) {
        if (refreshed) {
          console.debug('Token was successfully refreshed')
        } else {
          console.debug('Token is still valid')
        }
      })
      .catch(function () {
        console.error('Failed to refresh the token, or the session has expired')
      })
  }, [])

  useEffect(() => {
    // Refresh Data
    console.debug('Authorizing the user with the API')
    dispatch(getRsuData())
  }, [authLoginData, dispatch])

  useEffect(() => {}, [organizationName])

  return (
    <ReactKeycloakProvider
      initOptions={{ onLoad: 'login-required' }}
      authClient={keycloak}
      onTokens={({ token }: { token: string }) => {
        // Logic to prevent multiple login triggers
        if (!loginDispatched && token) {
          console.debug('onTokens loginDispatched:')
          dispatch(keycloakLogin(token))
          loginDispatched = true
        }
        setTimeout(() => (loginDispatched = false), 5000)
      }}
    >
      <div id="masterdiv" style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
        <Header />
        {authLoginData && keycloak?.authenticated ? (
          <div style={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
            <VerticalTabs
              notFoundRoute={
                <NotFound
                  redirectRoute="/dashboard/admin"
                  redirectRouteName="Admin Page"
                  description="This page does not exist. Please return to the main admin page."
                />
              }
              defaultTabIndex={0}
              tabs={[
                {
                  path: 'map',
                  title: 'RSU Map',
                  child: (
                    <>
                      <Menu />
                      <Map auth={true} />
                    </>
                  ),
                  icon: <MapIcon />,
                },
                {
                  path: 'intersectionMap',
                  title: 'Intersection Map',
                  child: <IntersectionMapView />,
                  icon: <TrafficIcon />,
                },
                {
                  path: 'intersectionDashboard',
                  title: 'Intersection Dashboard',
                  child: <IntersectionDashboard />,
                  icon: <DashboardIcon />,
                },
                {
                  path: 'admin',
                  title: 'Admin',
                  child: <Admin />,
                  icon: <ManageAccountsIcon />,
                },
                {
                  path: 'help',
                  title: 'Help',
                  child: <Help />,
                  icon: <HelpIcon />,
                },
                {
                  path: 'settings',
                  title: 'User Settings',
                  child: <AdminNotificationTab />,
                  icon: <SettingsIcon />,
                },
              ]}
            />
          </div>
        ) : (
          <div></div>
        )}
        <RingLoader css={loadercss} size={200} color={'#13d48d'} loading={loadingGlobal} speedMultiplier={1} />
      </div>
    </ReactKeycloakProvider>
  )
}

const loadercss = css`
  display: block;
  margin: 0 auto;
  position: absolute;
  top: 50%;
  left: 50%;
  margin-top: -125px;
  margin-left: -125px;
`

export default Dashboard
