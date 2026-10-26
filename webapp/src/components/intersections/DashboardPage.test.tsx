import { vi } from 'vitest'
import { ThemeProvider } from '@mui/material'
import { act, render } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { replaceChaoticIds } from '../../utils/test-utils'
import AssessmentsApi from '../../apis/intersections/assessments-api'
import NotificationApi from '../../apis/intersections/notification-api'
import DashboardPage from './DashboardPage'

beforeEach(() => {
  vi.spyOn(AssessmentsApi, 'getLatestAssessment').mockResolvedValue(undefined as any)
  vi.spyOn(NotificationApi, 'getActiveNotifications').mockResolvedValue([])
})

afterEach(() => {
  vi.restoreAllMocks()
})

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
          <DashboardPage />
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )
  await act(async () => {})

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
