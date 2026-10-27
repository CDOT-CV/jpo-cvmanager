import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { beforeEach, expect, it, vi } from 'vitest'
import fetchMock from 'jest-fetch-mock'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import FirmwareUpgradeStatusDialog from './FirmwareUpgradeStatusDialog'
import { FirmwareUpgradeStatus } from '../../models/Firmware'

const snapshot: FirmwareUpgradeStatus = {
  active_upgrades: { '192.0.2.1': { manufacturer: 'Commsignia', model: 'ITS-RS4-M', target_firmware_version: 'v2' } },
  upgrade_queue: ['192.0.2.3', '192.0.2.2'],
}
beforeEach(() => fetchMock.resetMocks())

const renderDialog = (store = setupStore(), onClose = vi.fn()) => render(
  <Provider store={store}><ThemeProvider theme={testTheme}>
    <FirmwareUpgradeStatusDialog onClose={onClose} />
  </ThemeProvider></Provider>
)

it('loads on open and displays active details separately from queue order', async () => {
  fetchMock.mockResponseOnce(JSON.stringify(snapshot))
  const close = vi.fn()
  renderDialog(undefined, close)
  expect(screen.getByLabelText('Loading firmware upgrade status')).toBeVisible()
  const active = await screen.findByRole('table', { name: 'Active upgrades' })
  expect(within(active).getByText('192.0.2.1')).toBeVisible()
  expect(within(active).getByText('v2')).toBeVisible()
  const queue = screen.getByRole('table', { name: 'Queued upgrades' })
  const rows = within(queue).getAllByRole('row')
  expect(within(rows[1]).getByRole('cell', { name: '1' })).toBeVisible()
  expect(within(rows[1]).getByRole('cell', { name: '192.0.2.3' })).toBeVisible()
  expect(within(rows[2]).getByRole('cell', { name: '2' })).toBeVisible()
  expect(within(rows[2]).getByRole('cell', { name: '192.0.2.2' })).toBeVisible()
  expect(screen.getByText('Active (1)')).toBeVisible()
  expect(screen.getByText('Queued (2)')).toBeVisible()
  expect(screen.queryByText('Close')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Close' }))
  expect(close).toHaveBeenCalledOnce()
})

it('refreshes from the API and replaces the previous snapshot with an idle state', async () => {
  fetchMock.mockResponseOnce(JSON.stringify(snapshot))
  renderDialog()
  await screen.findByText('192.0.2.1')
  fetchMock.mockResponseOnce(JSON.stringify({ active_upgrades: {}, upgrade_queue: [] }))
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  expect(await screen.findByText('No active firmware upgrades.')).toBeVisible()
  expect(screen.getByText('No queued firmware upgrades.')).toBeVisible()
  expect(screen.queryByText('192.0.2.1')).not.toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('shows an error rather than an empty or stale snapshot and can retry', async () => {
  fetchMock.mockResponseOnce(JSON.stringify(snapshot))
  renderDialog()
  await screen.findByText('192.0.2.1')
  fetchMock.mockResponseOnce('{}', { status: 502 })
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to retrieve firmware upgrade status')
  expect(screen.queryByText('No active firmware upgrades.')).not.toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  fetchMock.mockResponseOnce(JSON.stringify(snapshot))
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  expect(await screen.findByText('192.0.2.1')).toBeVisible()
})

it('requests fresh status when reopened even if an older snapshot is cached', async () => {
  const store = setupStore()
  fetchMock.mockResponseOnce(JSON.stringify(snapshot))
  const view = renderDialog(store)
  await screen.findByText('192.0.2.1')
  view.unmount()
  fetchMock.mockResponseOnce(JSON.stringify({ active_upgrades: {}, upgrade_queue: [] }))
  renderDialog(store)
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  expect(await screen.findByText('No active firmware upgrades.')).toBeVisible()
})
