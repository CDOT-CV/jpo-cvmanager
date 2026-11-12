import { render, screen } from '@testing-library/react'
import SnmpsetMenu from './SnmpsetMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <SnmpsetMenu type={undefined} rsuIpList={undefined} />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows the owner-only notice instead of the SNMP set form for a viewer organization', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <SnmpsetMenu type="single_rsu" rsuIpList={['192.0.2.10']} isOwnerOrg={false} ownerOrganization="Owner Org" />
      </Provider>
    </ThemeProvider>
  )
  expect(screen.getByRole('alert')).toHaveTextContent('This RSU is owned by "Owner Org"')
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})
