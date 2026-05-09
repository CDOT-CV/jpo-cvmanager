import { render } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from './styles'
import { setupStore } from './store'
import { replaceChaoticIds } from './utils/test-utils'
import Unsubscribe from './Unsubscribe'
import { MemoryRouter } from 'react-router-dom'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <MemoryRouter initialEntries={['/?token=fake-test-token']}>
          <Unsubscribe />
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
