import { render, screen } from '@testing-library/react'
import ConfigureRSU from './ConfigureRSU'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { vi } from 'vitest'
import { RsuInfo } from '../../models/RsuApi'

vi.mock('../../components/SnmpwalkMenu', () => ({ default: () => null }))
vi.mock('../../components/SnmpsetMenu', () => ({ default: () => null }))
vi.mock('../../components/RsuRebootMenu', () => ({ default: () => null }))
vi.mock('../../components/RsuFirmwareMenu', () => ({ default: () => null }))

vi.mock('../api/rsuCountsApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/rsuCountsApiSlice')>()
  return {
    ...actual,
    useGetRsuCountsByIpQuery: () => ({
      data: [
        {
          message_type: 'BSM',
          rsu_ip: '10.0.0.16',
          ode_input_count: 327817,
          ode_output_count: 327745,
          road: 'I70',
        },
        {
          message_type: 'MAP',
          rsu_ip: '10.0.0.16',
          ode_input_count: 12,
          ode_output_count: 11,
          road: 'I70',
        },
      ],
      isFetching: false,
      isError: false,
    }),
  }
})

const selectedRsu: RsuInfo = {
  id: 1,
  type: 'Feature',
  geometry: {
    type: 'Point',
    coordinates: [-104.99, 39.74],
  },
  properties: {
    rsu_id: 1,
    milepost: 6,
    geography: '',
    model_name: 'Kapsch',
    ipv4_address: '10.0.0.16',
    primary_route: 'I70',
    serial_number: 'E0006',
    manufacturer_name: 'Kapsch',
    tim_deposit: false,
  },
}

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows counts for all configured message types, not just the selected type', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          rsu: {
            value: {
              selectedRsu,
            },
          },
        })}
      >
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  expect(screen.getByText('Message Counts')).toBeInTheDocument()
  expect(screen.getByText('BSM')).toBeInTheDocument()
  expect(screen.getByText('MAP')).toBeInTheDocument()
  expect(screen.getByText('SPAT')).toBeInTheDocument()
  expect(screen.getByText('327,817')).toBeInTheDocument()
  expect(screen.getByText('327,745')).toBeInTheDocument()
  expect(screen.getByText('12')).toBeInTheDocument()
  expect(screen.queryByText('BSM Input')).not.toBeInTheDocument()
})
