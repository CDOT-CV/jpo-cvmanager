import React from 'react'
import { render } from '@testing-library/react'
import AdminRsuTab from './AdminRsuTab'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'
import * as adminRsuTabSlice from './adminRsuTabSlice'

it('should take a snapshot', () => {
  // The RSU updateTableData thunk dispatches another async action (getRsuInfoOnly),
  // which causes errors during snapshot tests without a full Redux store.
  // This mock replaces it with a no-op thunk to prevent those side effects.
  jest.spyOn(adminRsuTabSlice, 'updateTableData').mockImplementation(() => () => Promise.resolve());

  jest.spyOn(adminRsuTabSlice, 'updateTableData').mockImplementation(() => () => Promise.resolve())
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({ adminRsuTab: { loading: false, value: { activeDiv: 'rsu_table' } } })}>
        <BrowserRouter>
          <AdminRsuTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
