import { ThemeProvider } from '@mui/material'
import { act, render } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { replaceChaoticIds } from '../../utils/test-utils'
import ConfigurationPage from './ConfigurationPage'

const parameters = [
  {
    id: 'param-1',
    key: 'spat.minimumDataAggregation',
    category: 'General',
    value: 10,
    units: 'messages',
    description: 'Minimum number of SPaT messages required',
    updateType: 'INTERSECTION',
  },
  {
    id: 'param-2',
    key: 'map.broadcastRate',
    category: 'General',
    value: 1,
    units: 'Hz',
    description: 'Expected MAP broadcast rate',
    updateType: 'DEFAULT',
  },
]

jest.mock('../../features/api/intersectionConfigSlice', () => ({
  ...jest.requireActual('../../features/api/intersectionConfigSlice'),
  useGetIntersectionParametersQuery: () => ({ data: parameters, refetch: jest.fn() }),
}))

it('should take a snapshot', async () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { token: 'test-token' } } },
          intersection: { value: { selectedIntersectionId: 12109 } },
        } as any)}
      >
        <MemoryRouter>
          <ConfigurationPage />
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )
  await act(async () => {})

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
