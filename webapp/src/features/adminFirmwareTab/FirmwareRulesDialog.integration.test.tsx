import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import fetchMock from 'jest-fetch-mock'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { FirmwareRuleOptions } from '../../models/Firmware'
import FirmwareRulesDialog from './FirmwareRulesDialog'

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn() } }))

const source = { firmware_id: 1, manufacturer: 'Commsignia', model: 'ITS-RS4-M', version: 'v1', legacy: true }
const destination = { ...source, firmware_id: 2, version: 'v2', legacy: false }
const rule = { rule_id: 10, source, destination, legacy_destination: false }

describe('Firmware rule refresh', () => {
  let serverOptions: FirmwareRuleOptions

  beforeEach(() => {
    fetchMock.resetMocks()
    serverOptions = { destination, sources: [source], rules: [], can_target: true }
    // Exercise the real RTK Query hooks against a changing server response.
    fetchMock.mockResponse(async request => {
      if (request.method === 'GET' && request.url.endsWith('/images/2/upgrade-rules')) {
        return JSON.stringify(serverOptions)
      }
      if (request.method === 'PUT' && request.url.endsWith('/images/2/upgrade-rules')) {
        serverOptions = { ...serverOptions, rules: [rule] }
        return { body: '', status: 204 }
      }
      if (request.method === 'DELETE' && request.url.endsWith('/upgrade-rules/10?expected_target_id=2')) {
        serverOptions = { ...serverOptions, rules: [] }
        return { body: '', status: 204 }
      }
      throw new Error(`Unexpected request: ${request.method} ${request.url}`)
    })
  })

  const renderDialog = () => render(
    <Provider store={setupStore()}>
      <ThemeProvider theme={testTheme}>
        <FirmwareRulesDialog firmwareId={2} onClose={vi.fn()} onChanged={vi.fn()} />
      </ThemeProvider>
    </Provider>
  )

  it('shows the newly saved rule after fetching updated options', async () => {
    renderDialog()
    fireEvent.click(await screen.findByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v1.*No target/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Save Rules' }))

    const table = await screen.findByRole('table', { name: 'Upgrades to this firmware' })
    expect(within(table).getByRole('cell', { name: 'v1' })).toBeInTheDocument()
    expect(within(table).getByRole('cell', { name: 'v2' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByRole('checkbox', { name: /v1.*Target: v2/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /v1.*Target: v2/ })).toBeDisabled()
  })

  it('removes a deleted rule from the table after fetching updated options', async () => {
    serverOptions = { ...serverOptions, rules: [rule] }
    renderDialog()
    fireEvent.click(await screen.findByRole('button', { name: 'Delete Rule' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText('No versions have a rule to this firmware.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Delete Rule' })).not.toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByRole('checkbox', { name: /v1.*No target/ })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: /v1.*No target/ })).not.toBeDisabled()
  })
})
