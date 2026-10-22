import { render, screen } from '@testing-library/react'
import RsuRebootMenu from './RsuRebootMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <RsuRebootMenu />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('does not show another RSU\'s accepted reboot as this device\'s result', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({
        rsu: { value: { selectedRsu: { properties: { ipv4_address: '192.0.2.11' } } } },
        config: { value: { rebootChangeSuccess: true, rebootRequestedIp: '192.0.2.10' } },
      })}>
        <RsuRebootMenu />
      </Provider>
    </ThemeProvider>
  )
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})
