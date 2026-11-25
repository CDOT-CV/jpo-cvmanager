import '@testing-library/jest-dom'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import RsuFirmwareMenu from './RsuFirmwareMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'

import { confirmAlert } from 'react-confirm-alert'
import { vi } from 'vitest'
import RsuFirmwareApi from '../apis/intersections/rsu-firmware-api'

vi.mock('react-confirm-alert')
beforeEach(() => vi.mocked(confirmAlert).mockClear())
afterEach(() => vi.restoreAllMocks())
const configWithMsg = (firmwareUpgradeMsg: string, firmwareUpgradeErr: boolean) => ({
  config: {
    value: {
      firmwareUpgradeAvailable: false,
      firmwareUpgradeName: '',
      firmwareUpgradeMsg,
      firmwareUpgradeErr,
    },
  },
})

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <RsuFirmwareMenu type={''} rsuIpList={[]} />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('RsuFirmwareMenu message block (single_rsu type)', () => {
  it('renders role="status" when firmwareUpgradeMsg is a success string', () => {
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore(configWithMsg('Upgrade submitted successfully.', false))}>
          <RsuFirmwareMenu type="single_rsu" rsuIpList={['10.0.0.1']} />
        </Provider>
      </ThemeProvider>
    )

    const statusEl = screen.getByRole('status')
    expect(statusEl).toBeInTheDocument()
    expect(statusEl).toHaveTextContent('Upgrade submitted successfully.')
  })

  it('renders role="alert" when firmwareUpgradeErr is true', () => {
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore(configWithMsg('Upgrade failed: timeout', true))}>
          <RsuFirmwareMenu type="single_rsu" rsuIpList={['10.0.0.1']} />
        </Provider>
      </ThemeProvider>
    )

    const alertEl = screen.getByRole('alert')
    expect(alertEl).toBeInTheDocument()
    expect(alertEl).toHaveTextContent('Upgrade failed: timeout')
  })

  it('does not render the message block when firmwareUpgradeMsg is empty string', () => {
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore(configWithMsg('', false))}>
          <RsuFirmwareMenu type="single_rsu" rsuIpList={['10.0.0.1']} />
        </Provider>
      </ThemeProvider>
    )

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

const renderMenu = (ips = ['192.0.2.10', '192.0.2.11'], type = 'multi_rsu') => render(
  <Provider store={setupStore({})}><ThemeProvider theme={testTheme}>
    <RsuFirmwareMenu type={type} rsuIpList={ips} />
  </ThemeProvider></Provider>
)

const confirmUpgrade = async () => {
  fireEvent.click(screen.getByRole('button', { name: 'Check For Upgrade Availability' }))
  const options = vi.mocked(confirmAlert).mock.calls.at(-1)![0]
  await act(async () => options.buttons!.find((button) => button.label === 'Yes')!.onClick!())
}

it('reports each selected RSU, with failures first and explanatory tooltips', async () => {
  vi.spyOn(RsuFirmwareApi, 'postRsuUpgradeData').mockResolvedValue({ status: 200, message: '', body: {
    '192.0.2.10': { code: 200, data: { message: 'started' } },
    '192.0.2.11': { code: 409, data: 'RSU is already up to date' },
    '192.0.2.12': { code: 500, data: 'Device unreachable' },
  } })
  renderMenu(['192.0.2.10', '192.0.2.11', '192.0.2.12', '192.0.2.13'])
  await confirmUpgrade()

  const table = screen.getByRole('table', { name: 'Firmware upgrade results by RSU' })
  const rows = within(table).getAllByRole('row').slice(1)
  expect(rows.map((row) => within(row).getAllByRole('cell')[0].textContent))
    .toEqual(['192.0.2.12', '192.0.2.13', '192.0.2.10', '192.0.2.11'])
  expect(screen.getByRole('status')).toHaveTextContent('2 successful · 2 need attention')
  expect(screen.getByLabelText('192.0.2.10: Upgrade start accepted')).toContainElement(within(rows[2]).getByTestId('CheckIcon'))
  expect(screen.getByLabelText('192.0.2.11: Already up to date')).toContainElement(within(rows[3]).getByTestId('CheckIcon'))
  expect(screen.getByLabelText('192.0.2.13: No response; check upgrade status before retrying'))
    .toContainElement(within(rows[1]).getByTestId('CloseIcon'))
  const failure = screen.getByLabelText('192.0.2.12: Device unreachable')
  expect(failure).toHaveAttribute('tabindex', '0')
  fireEvent.mouseOver(failure)
  expect(await screen.findByRole('tooltip')).toHaveTextContent('Device unreachable')
  fireEvent.click(screen.getByRole('button', { name: 'Dismiss firmware upgrade results' }))
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

it('shows request failures for every selected RSU', async () => {
  vi.spyOn(RsuFirmwareApi, 'postRsuUpgradeData').mockResolvedValue({ status: 403, message: 'Not authorized', body: {} })
  renderMenu()
  await confirmUpgrade()
  expect(screen.getByLabelText('192.0.2.10: Not authorized')).toBeVisible()
  expect(screen.getByLabelText('192.0.2.11: Not authorized')).toBeVisible()
  expect(screen.getByRole('status')).toHaveTextContent('0 successful · 2 need attention')
})

it('does not report success when the batch response contains no results', async () => {
  vi.spyOn(RsuFirmwareApi, 'postRsuUpgradeData').mockResolvedValue({ status: 200, message: '', body: undefined })
  renderMenu()
  await confirmUpgrade()
  expect(screen.getByRole('status')).toHaveTextContent('0 successful · 2 need attention')
  expect(screen.getByLabelText('192.0.2.10: No response; check upgrade status before retrying')).toBeVisible()
})

it('shows a batch table even when only one RSU is selected', async () => {
  vi.spyOn(RsuFirmwareApi, 'postRsuUpgradeData').mockResolvedValue({ status: 200, message: '', body: {
    '192.0.2.10': { code: 200, data: 'started' },
  } })
  renderMenu(['192.0.2.10'])
  await confirmUpgrade()
  expect(screen.getByRole('table', { name: 'Firmware upgrade results by RSU' })).toBeVisible()
  expect(screen.getByRole('status')).toHaveTextContent('1 successful · 0 need attention')
})

it('waits for the response, disables repeat submissions, and does not submit on decline', async () => {
  let finish!: (value: null) => void
  const request = vi.spyOn(RsuFirmwareApi, 'postRsuUpgradeData')
    .mockReturnValue(new Promise((resolve) => { finish = resolve }))
  renderMenu()
  fireEvent.click(screen.getByRole('button', { name: 'Check For Upgrade Availability' }))
  const options = vi.mocked(confirmAlert).mock.calls[0][0]
  options.buttons!.find((button) => button.label === 'No')!.onClick!()
  expect(request).not.toHaveBeenCalled()
  let submitted: unknown
  act(() => { submitted = options.buttons!.find((button) => button.label === 'Yes')!.onClick!() })
  expect(screen.getByRole('button', { name: 'Check For Upgrade Availability' })).toBeDisabled()
  expect(screen.getByRole('status')).toHaveTextContent('Requesting firmware upgrades')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  await act(async () => { finish(null); await submitted })
  expect(screen.getByRole('button', { name: 'Check For Upgrade Availability' })).toBeEnabled()
  expect(screen.getByRole('status')).toHaveTextContent('0 successful · 2 need attention')
})
