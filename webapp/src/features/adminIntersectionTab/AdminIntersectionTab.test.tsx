import { render } from '@testing-library/react'
import AdminIntersectionTab from './AdminIntersectionTab'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

const mockTableData = [
  {
    intersection_id: 'int-1',
    intersection_name: 'Test Intersection 1',
    origin_ip: '192.168.1.1',
    rsus: 'RSU-001, RSU-002',
  },
  {
    intersection_id: 'int-2',
    intersection_name: 'Test Intersection 2',
    origin_ip: '192.168.1.2',
    rsus: 'RSU-003',
  },
]

test('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          adminIntersectionTab: {
            loading: false,
            value: {
              activeDiv: 'intersection_table',
              tableData: mockTableData,
              title: 'Intersections',
              columns: [
                { title: 'Intersection ID', field: 'intersection_id', id: 0 },
                { title: 'Intersection Name', field: 'intersection_name', id: 1 },
                { title: 'Origin IP', field: 'origin_ip', id: 2 },
                { title: 'Linked RSUs', field: 'rsus', id: 3 },
              ],
              editIntersectionRowData: {},
            },
          },
        })}
      >
        <BrowserRouter>
          <AdminIntersectionTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
