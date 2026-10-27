import { fireEvent, render, screen, within, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import AdminAddRsu from './AdminAddRsu'
import { useGetRsuAllowedSelectionsQuery, useGetRsuFirmwareOptionsQuery, useCreateRsuMutation } from '../api/rsuApiSlice'

vi.mock('../api/rsuApiSlice', async (original) => ({
  ...await original<typeof import('../api/rsuApiSlice')>(),
  useGetRsuAllowedSelectionsQuery: vi.fn(), useGetRsuFirmwareOptionsQuery: vi.fn(), useCreateRsuMutation: vi.fn(),
}))
const create = vi.fn()
const refetch = vi.fn()
const model = 'Commsignia ITS-RS4-M'
const otherModel = 'Yunex RSU2X'
const options = [{ firmware_id: 5, version: 'v1', legacy: true }, { firmware_id: 6, version: 'v2', legacy: false }]
const renderForm = () => render(<ThemeProvider theme={testTheme}><Provider store={setupStore()}>
  <BrowserRouter><AdminAddRsu /></BrowserRouter>
</Provider></ThemeProvider>)
const choose = (id: string, option: string) => {
  fireEvent.mouseDown(document.getElementById(id)!)
  fireEvent.click(within(screen.getByRole('listbox')).getByRole('option', { name: option }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(useGetRsuAllowedSelectionsQuery).mockReturnValue({ data: {
    rsu_models: [model, otherModel], primary_routes: ['I-25'], ssh_credential_groups: ['ssh'],
    snmp_credential_groups: ['snmp'], snmp_version_groups: ['v3'], organizations: ['Org'],
  } } as any)
  vi.mocked(useCreateRsuMutation).mockReturnValue([create, {}] as any)
  vi.mocked(useGetRsuFirmwareOptionsQuery).mockImplementation((name) => ({
    currentData: name === model ? options : undefined, refetch,
  } as any))
})

it('loads options after model selection and clears the installed version when the model changes', () => {
  renderForm()
  expect(screen.getByRole('combobox', { name: /Installed Firmware/ })).toHaveAttribute('aria-disabled', 'true')
  choose('model', model)
  choose('firmware_id', 'v1 (Legacy)')
  expect(screen.getByRole('combobox', { name: /Installed Firmware/ })).toHaveTextContent('v1 (Legacy)')
  choose('model', otherModel)
  expect(screen.getByRole('combobox', { name: /Installed Firmware/ })).not.toHaveTextContent('v1')
  expect(useGetRsuFirmwareOptionsQuery).toHaveBeenLastCalledWith(otherModel, expect.objectContaining({ skip: false }))
})

it('explains an empty firmware catalog without offering an unknown option', () => {
  vi.mocked(useGetRsuFirmwareOptionsQuery).mockReturnValue({ currentData: [], refetch } as any)
  renderForm()
  choose('model', model)
  expect(screen.getByText(/No eligible firmware exists/)).toBeVisible()
  expect(screen.getByRole('combobox', { name: /Installed Firmware/ })).toBeRequired()
  expect(create).not.toHaveBeenCalled()
})

it('requires installed firmware and includes the selected ID in the creation request', async () => {
  create.mockReturnValue({ unwrap: () => Promise.resolve() })
  renderForm()
  for (const [label, value] of [
    ['RSU IP', '192.0.2.1'], ['Latitude', '39.7'], ['Longitude', '-104.9'],
    ['Milepost', '1'], ['Serial Number', 'SN-1'], ['SCMS ID', 'id'],
  ]) {
    fireEvent.change(screen.getByLabelText(new RegExp(label)), { target: { value } })
  }
  choose('primary_route', 'I-25')
  choose('model', model)
  choose('ssh_credential_group', 'ssh')
  choose('snmp_credential_group', 'snmp')
  choose('snmp_version_group', 'v3')
  choose('organizations', 'Org')
  // MUI's multiple selection stays open until dismissed.
  fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' })
  fireEvent.submit(document.getElementById('add-rsu-form')!)
  expect(await screen.findByText('Must select installed firmware')).toBeVisible()
  expect(create).not.toHaveBeenCalled()
  choose('firmware_id', 'v2')
  fireEvent.submit(document.getElementById('add-rsu-form')!)
  await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({ firmware_id: 6, model })))
})

it('disables firmware selection while loading or when the request fails and offers retry', () => {
  vi.mocked(useGetRsuFirmwareOptionsQuery).mockReturnValue({ isFetching: true, refetch } as any)
  const view = renderForm()
  choose('model', model)
  expect(screen.getByText('Loading firmware versions...')).toBeVisible()
  vi.mocked(useGetRsuFirmwareOptionsQuery).mockReturnValue({ isError: true, refetch } as any)
  view.rerender(<ThemeProvider theme={testTheme}><Provider store={setupStore()}>
    <BrowserRouter><AdminAddRsu /></BrowserRouter>
  </Provider></ThemeProvider>)
  expect(screen.getByRole('combobox', { name: /Installed Firmware/ })).toHaveAttribute('aria-disabled', 'true')
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
  expect(refetch).toHaveBeenCalledOnce()
})
