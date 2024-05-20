import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import React, { useEffect } from 'react'
import { useDispatch } from 'react-redux'
import { Link } from 'react-router-dom'
import { RootState } from '../store'
import { setRouteNotFound } from '../generalSlices/userSlice'
import { useNavigate } from 'react-router-dom'
import { Button, Typography } from '@mui/material'

type NotFoundProps = {
  redirectRoute?: string
  redirectRouteName?: string
  description?: string
  shouldRedirect?: boolean
  offsetHeight?: number
}
export const NotFound = ({
  redirectRoute = '/dashboard',
  redirectRouteName = 'Main Dashboard',
  description = 'This route does not exist. Please return to the main dashboard.',
  shouldRedirect = false,
  offsetHeight = 135,
}: NotFoundProps) => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const navigate = useNavigate()

  useEffect(() => {
    if (shouldRedirect) {
      dispatch(setRouteNotFound(true))
    }
  }, [])
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        flexDirection: 'column',
        alignItems: 'center',
        width: '100%',
        height: 'calc(100vh - ' + offsetHeight + 'px)',
        backgroundColor: '#15317e',
      }}
    >
      <Typography variant={'h1'} color={'white'}>
        404 - Page Not Found
      </Typography>
      <br />
      <Typography variant={'h3'} color={'white'}>
        {description}
      </Typography>
      <br />
      <Button
        variant="contained"
        style={{ fontSize: '20px' }}
        onClick={() => {
          dispatch(setRouteNotFound(false))
          navigate(redirectRoute)
        }}
      >
        {redirectRouteName}
      </Button>
      {redirectRoute !== '/dashboard' ? (
        <>
          <br />
          <Button
            variant="contained"
            style={{ fontSize: '20px' }}
            onClick={() => {
              dispatch(setRouteNotFound(false))
              navigate('/dashboard')
            }}
          >
            {'Main Dashboard'}
          </Button>
        </>
      ) : (
        <></>
      )}
      <h1 style={{ color: 'white' }}></h1>
    </div>
  )
}