import { ThemeProvider } from '@mui/material'
import { act, render } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import NotificationApi from '../../../apis/intersections/notification-api'
import { setupStore } from '../../../store'
import { testTheme } from '../../../styles'
import { replaceChaoticIds } from '../../../utils/test-utils'
import { NotificationsTable } from './notifications-table'

const notifications = [
  {
    key: 'notif-1',
    notificationType: 'SpatBroadcastRateNotification',
    notificationGeneratedAt: new Date('2025-12-04T12:00:00Z').getTime(),
    notificationText: 'SPaT broadcast rate is below threshold',
  },
  {
    key: 'notif-2',
    notificationType: 'IntersectionReferenceAlignmentNotification',
    notificationGeneratedAt: new Date('2025-12-04T13:30:00Z').getTime(),
    notificationText: 'Intersection reference alignment mismatch',
    event: {
      mapRegulatorIntersectionIds: ['12109'],
      spatRegulatorIntersectionIds: ['12110'],
    },
  },
] as any

beforeEach(() => {
  jest.spyOn(NotificationApi, 'getActiveNotifications').mockResolvedValue(notifications)
})

afterEach(() => {
  jest.restoreAllMocks()
})

const renderTable = async (simple: boolean) => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { token: 'test-token' } } },
          intersection: { value: { selectedIntersectionId: 12109 } },
        } as any)}
      >
        <MemoryRouter>
          <NotificationsTable simple={simple} />
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )
  await act(async () => {})
}

it('should take a snapshot', async () => {
  await renderTable(false)
  expect(replaceChaoticIds(document.body)).toMatchSnapshot()
})

it('should take a snapshot in simple mode', async () => {
  await renderTable(true)
  expect(replaceChaoticIds(document.body)).toMatchSnapshot()
})
