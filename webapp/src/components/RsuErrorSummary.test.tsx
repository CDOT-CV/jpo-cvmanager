import React from 'react'
import { render } from '@testing-library/react'
import RsuErrorSummary from './RsuErrorSummary'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({ user: { value: { authLoginData: { token: 'token' } } } })}>
        <RsuErrorSummary
          rsu={'string'}
          online_status={''}
          scms_status={''}
          hidden={false}
          setHidden={function (): void {
            return null
          }}
        />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
