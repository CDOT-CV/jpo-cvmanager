import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import { testTheme } from '../../styles'
import FirmwareRulesDialog from './FirmwareRulesDialog'
import { FirmwareRuleOptions } from '../../models/Firmware'
import {
  useAssignFirmwareRulesMutation, useDeleteFirmwareRuleMutation, useGetFirmwareRuleOptionsQuery,
} from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useAssignFirmwareRulesMutation: vi.fn(), useDeleteFirmwareRuleMutation: vi.fn(), useGetFirmwareRuleOptionsQuery: vi.fn(),
}))
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn() } }))

const source = { firmware_id: 1, manufacturer: 'Commsignia', model: 'ITS-RS4-M', version: 'v1', legacy: true }
const oldTarget = { ...source, firmware_id: 2, version: 'v2' }
const destination = { ...source, firmware_id: 3, version: 'v3', legacy: false }
const options: FirmwareRuleOptions = {
  destination, can_target: true, sources: [source, oldTarget],
  rules: [{ rule_id: 10, source, destination: oldTarget, legacy_destination: true }],
}
const assign = vi.fn()
const remove = vi.fn()
const refetch = vi.fn()
const changed = vi.fn()
const close = vi.fn()
const renderDialog = () => render(<ThemeProvider theme={testTheme}>
  <FirmwareRulesDialog firmwareId={3} onClose={close} onChanged={changed} />
</ThemeProvider>)

describe('Firmware upgrade rules', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({ currentData: options, refetch } as any)
    vi.mocked(useAssignFirmwareRulesMutation).mockReturnValue([assign] as any)
    vi.mocked(useDeleteFirmwareRuleMutation).mockReturnValue([remove] as any)
    assign.mockReturnValue({ unwrap: () => Promise.resolve() })
    remove.mockReturnValue({ unwrap: () => Promise.resolve() })
  })

  it('expands the incoming-rule editor on demand and discards selections on cancel', () => {
    renderDialog()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    const incoming = screen.getByRole('region', { name: 'Upgrades to this firmware' })
    expect(screen.getByRole('region', { name: 'Upgrade from this firmware' })).toBeVisible()
    fireEvent.click(within(incoming).getByRole('button', { name: 'New' }))
    expect(within(incoming).getByRole('group', { name: 'Source firmware versions' })).toBeVisible()
    expect(within(incoming).getByRole('button', { name: 'New' })).toBeDisabled()
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    fireEvent.click(within(incoming).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(assign).not.toHaveBeenCalled()
    expect(within(incoming).getByRole('button', { name: 'New' })).toBeEnabled()
    fireEvent.click(within(incoming).getByRole('button', { name: 'New' }))
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).not.toBeChecked()
    expect(within(incoming).getByRole('button', { name: 'Save Rules' })).toBeDisabled()
  })

  it('confirms reassignment and submits the old destinations for all selected versions', async () => {
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v1.*Target: v2/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Save Rules' }))
    expect(assign).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Redirect 1 existing rule to v3?')
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(changed).toHaveBeenCalledTimes(1))
    expect(assign).toHaveBeenCalledWith({ destinationId: 3, sources: [
      { source_id: 1, expected_target_id: 2 }, { source_id: 2, expected_target_id: null },
    ] })
    expect(refetch).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('group', { name: 'Source firmware versions' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New' })).toBeEnabled()
  })

  it('keeps the selection and displays the conflict when saving fails', async () => {
    assign.mockReturnValue({ unwrap: () => Promise.reject({ data: { detail: 'Upgrade rules changed. Close and reopen the dialog before saving.' } }) })
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Save Rules' }))
    expect(await screen.findByText('Upgrade rules changed. Close and reopen the dialog before saving.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).toBeChecked()
    expect(close).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'New' })).toBeDisabled()
    expect(changed).not.toHaveBeenCalled()
  })

  it('opens a fresh editor after closing the dialog with unsaved selections', () => {
    const dialog = renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(close).toHaveBeenCalledTimes(1)
    // The firmware tab unmounts the dialog on close and mounts it on reopening.
    dialog.unmount()
    renderDialog()
    expect(screen.getByRole('button', { name: 'New' })).toBeEnabled()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).not.toBeChecked()
  })

  it('permits deleting legacy rules without enabling new rules to an unverified target', async () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: { ...options, can_target: false, rules: [{ ...options.rules[0], destination }] }, refetch,
    } as any)
    renderDialog()
    expect(screen.queryByRole('button', { name: 'New' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete Rule' }))
    expect(remove).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(changed).toHaveBeenCalledTimes(1))
    expect(remove).toHaveBeenCalledWith({ ruleId: 10, expectedTargetId: 3 })
  })

  it('marks only legacy rules in a list containing incoming and outgoing rules', () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: {
        ...options,
        destination: oldTarget,
        can_target: false,
        rules: [options.rules[0], { rule_id: 11, source: oldTarget, destination, legacy_destination: false }],
      },
      refetch,
    } as any)
    render(<ThemeProvider theme={testTheme}>
      <FirmwareRulesDialog firmwareId={2} onClose={close} onChanged={changed} />
    </ThemeProvider>)
    const incoming = screen.getByRole('table', { name: 'Upgrades to this firmware' })
    const outgoing = screen.getByRole('table', { name: 'Upgrade from this firmware' })
    expect(within(incoming).getByRole('cell', { name: 'v1' })).toBeInTheDocument()
    expect(within(incoming).getByRole('cell', { name: 'v2 (Legacy rule)' })).toBeInTheDocument()
    expect(within(incoming).queryByRole('cell', { name: 'v3' })).not.toBeInTheDocument()
    expect(within(outgoing).getByRole('cell', { name: 'v2' })).toBeInTheDocument()
    expect(within(outgoing).getByRole('cell', { name: 'v3' })).toBeInTheDocument()
    expect(within(outgoing).queryByRole('cell', { name: 'v1' })).not.toBeInTheDocument()
    expect(screen.getAllByText(/\(Legacy rule\)/)).toHaveLength(1)

    const incomingSection = screen.getByRole('region', { name: 'Upgrades to this firmware' })
    const outgoingSection = screen.getByRole('region', { name: 'Upgrade from this firmware' })
    fireEvent.click(within(incoming).getByRole('button', { name: 'Delete Rule' }))
    expect(within(incomingSection).getByRole('alert')).toHaveTextContent('Delete rule v1 → v2?')
    expect(within(outgoingSection).queryByRole('alert')).not.toBeInTheDocument()
    fireEvent.click(within(incomingSection).getByRole('button', { name: 'Cancel' }))
    expect(within(incomingSection).queryByRole('alert')).not.toBeInTheDocument()

    fireEvent.click(within(outgoing).getByRole('button', { name: 'Delete Rule' }))
    expect(within(outgoingSection).getByRole('alert')).toHaveTextContent('Delete rule v2 → v3?')
    expect(within(incomingSection).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('disables writes when options fail to load', () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({ isError: true, refetch } as any)
    renderDialog()
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load upgrade rules')
    expect(screen.queryByRole('button', { name: 'New' })).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Close and reopen this dialog to try again.')
    expect(screen.queryByRole('button', { name: 'Reload' })).not.toBeInTheDocument()
  })

  it('keeps rules visible during a storage outage without allowing assignments', () => {
    const message = 'Storage verification unavailable. Close and reopen to retry.'
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: { ...options, can_target: false, eligibility_error: message,
        rules: [{ ...options.rules[0], destination }] }, refetch,
    } as any)
    renderDialog()
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Delete Rule' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'New' })).not.toBeInTheDocument()
  })

  it('protects unsaved selections from rule deletion until they are cancelled', () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: { ...options, rules: [{ ...options.rules[0], destination }] }, refetch,
    } as any)
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    expect(screen.getByRole('button', { name: 'Delete Rule' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Delete Rule' }))
    expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('button', { name: 'Delete Rule' })).toBeEnabled()
  })

  it('shows loading instead of editable stale options during a refetch', () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({ currentData: options, isFetching: true, refetch } as any)
    renderDialog()
    expect(screen.getByLabelText('Loading upgrade rules')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete Rule' })).not.toBeInTheDocument()
  })

  it('keeps an existing rule and shows the error when deletion fails', async () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: { ...options, rules: [{ ...options.rules[0], destination }] }, refetch,
    } as any)
    remove.mockReturnValue({ unwrap: () => Promise.reject({ data: { detail: 'Deletion failed. Please retry.' } }) })
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'Delete Rule' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(await screen.findByText('Deletion failed. Please retry.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete Rule' })).toBeEnabled()
    expect(refetch).not.toHaveBeenCalled()
    expect(changed).not.toHaveBeenCalled()
  })

  it('disables mutation and close controls while saving', async () => {
    let resolveSave!: () => void
    assign.mockReturnValue({ unwrap: () => new Promise<void>(resolve => { resolveSave = resolve }) })
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /v2.*No target/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Save Rules' }))
    expect(screen.getByRole('button', { name: 'Save Rules' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).toBeDisabled()
    resolveSave()
    await waitFor(() => expect(screen.queryByRole('checkbox')).not.toBeInTheDocument())
  })

  it('prevents new selections during confirmation and duplicate requests while deleting', async () => {
    vi.mocked(useGetFirmwareRuleOptionsQuery).mockReturnValue({
      currentData: { ...options, rules: [{ ...options.rules[0], destination }] }, refetch,
    } as any)
    let resolveDelete!: () => void
    remove.mockReturnValue({ unwrap: () => new Promise<void>(resolve => { resolveDelete = resolve }) })
    renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete Rule' }))
    expect(screen.getByRole('checkbox', { name: /v2.*No target/ })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete Rule' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(remove).toHaveBeenCalledTimes(1)
    resolveDelete()
    await waitFor(() => expect(changed).toHaveBeenCalledTimes(1))
  })
})
