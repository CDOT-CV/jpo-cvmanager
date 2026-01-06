import React from 'react'
import { render } from '@testing-library/react'
import DisplayRsuErrors from './DisplayRsuErrors'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'

// Mock fetch to prevent actual API calls
global.fetch = jest.fn(() =>
  Promise.resolve({
    ok: true,
    json: () => Promise.resolve([]),
  })
) as jest.Mock

beforeAll(() => {
  jest.useFakeTimers()
  jest.setSystemTime(new Date('2024-10-01'))
})

afterAll(() => {
  jest.useRealTimers()
  jest.restoreAllMocks()
})

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: {
            value: {
              authLoginData: {
                token: 'token',
              },
            },
          },
        })}
      >
        <DisplayRsuErrors
          initialSelectedRsu={{
            id: 1,
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [],
            },
            properties: {
              rsu_id: 1,
              milepost: 1,
              geography: 'POINT (39.7392 -104.9903)',
              model_name: 'model_name',
              ipv4_address: '1.1.1.1',
              primary_route: 'primary_route',
              serial_number: 'serial_number',
              manufacturer_name: 'manufacturer_name',
            },
          }}
        />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})