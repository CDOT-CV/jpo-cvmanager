import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import toast from 'react-hot-toast'
import CredentialFormDialog from './CredentialFormDialog'
import { testTheme } from '../../styles'
import { CredentialSummary, useCreateCredentialMutation, useUpdateCredentialMutation } from '../api/credentialApiSlice'

vi.mock('../api/credentialApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/credentialApiSlice')>()),
  useCreateCredentialMutation: vi.fn(),
  useUpdateCredentialMutation: vi.fn(),
}))

const createCredential = vi.fn()
const updateCredential = vi.fn()
const onClose = vi.fn()

const existing: CredentialSummary = {
  nickname: 'ssh-main',
  username: 'admin',
  ownerOrganization: 'OrgA',
  rsuIps: ['10.0.0.1'],
}

const renderDialog = (credential?: CredentialSummary) =>
  render(
    <ThemeProvider theme={testTheme}>
      <CredentialFormDialog type="rsu" typeLabel="SSH" organization="OrgA" credential={credential} onClose={onClose} />
    </ThemeProvider>
  )

const setField = (label: RegExp, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })
const submit = () => fireEvent.submit(document.getElementById('rsu-credential-form')!)

beforeEach(() => {
  vi.clearAllMocks()
  createCredential.mockReturnValue({ unwrap: () => Promise.resolve() })
  updateCredential.mockReturnValue({ unwrap: () => Promise.resolve() })
  vi.mocked(useCreateCredentialMutation).mockReturnValue([createCredential, { isLoading: false }] as any)
  vi.mocked(useUpdateCredentialMutation).mockReturnValue([updateCredential, { isLoading: false }] as any)
})

describe('adding a credential', () => {
  it('requires nickname, username and password', async () => {
    renderDialog()

    submit()

    expect(await screen.findByText('Please enter a nickname')).toBeInTheDocument()
    expect(screen.getByText('Please enter a username')).toBeInTheDocument()
    expect(screen.getByText('Please enter a password')).toBeInTheDocument()
    expect(createCredential).not.toHaveBeenCalled()
  })

  it('rejects a nickname longer than the database column', async () => {
    renderDialog()

    setField(/Nickname/, 'n'.repeat(129))
    setField(/Username/, 'admin')
    setField(/Password/, 'secret')
    submit()

    expect(await screen.findByText('Nickname must be 128 characters or fewer')).toBeInTheDocument()
    expect(createCredential).not.toHaveBeenCalled()
  })

  it('creates the credential for the selected organization and closes', async () => {
    const success = vi.spyOn(toast, 'success')
    renderDialog()

    setField(/Nickname/, '  ssh-new ')
    setField(/Username/, ' admin ')
    setField(/Password/, ' secret ')
    submit()

    await waitFor(() =>
      expect(createCredential).toHaveBeenCalledWith({
        type: 'rsu',
        nickname: 'ssh-new',
        username: 'admin',
        password: ' secret ',
        organization: 'OrgA',
      })
    )
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(success).toHaveBeenCalledWith('SSH Credential Created Successfully')
  })

  it('shows the API error and stays open when creation fails', async () => {
    const error = vi.spyOn(toast, 'error')
    createCredential.mockReturnValue({
      unwrap: () => Promise.reject({ data: { detail: 'RSU Credential already exists' } }),
    })
    renderDialog()

    setField(/Nickname/, 'ssh-new')
    setField(/Username/, 'admin')
    setField(/Password/, 'secret')
    submit()

    await waitFor(() =>
      expect(error).toHaveBeenCalledWith(
        'Failed to create SSH credential due to error: RSU Credential already exists'
      )
    )
    expect(onClose).not.toHaveBeenCalled()
  })
})

describe('editing a credential', () => {
  it('locks the nickname and never shows the current password', () => {
    renderDialog(existing)

    expect(screen.getByText('Edit SSH Credential')).toBeInTheDocument()
    expect(screen.getByLabelText(/Nickname/)).toBeDisabled()
    expect(screen.getByLabelText(/Nickname/)).toHaveValue('ssh-main')
    expect(screen.getByLabelText(/Username/)).toHaveValue('admin')
    expect(screen.getByLabelText(/Password/)).toHaveValue('')
  })

  it('keeps the current password when the field is left blank', async () => {
    renderDialog(existing)

    setField(/Username/, 'newadmin')
    submit()

    await waitFor(() =>
      expect(updateCredential).toHaveBeenCalledWith({
        type: 'rsu',
        nickname: 'ssh-main',
        username: 'newadmin',
        password: undefined,
      })
    )
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('sends only the password when only the password changes', async () => {
    renderDialog(existing)

    setField(/Password/, 'newSecret')
    submit()

    await waitFor(() =>
      expect(updateCredential).toHaveBeenCalledWith({
        type: 'rsu',
        nickname: 'ssh-main',
        username: undefined,
        password: 'newSecret',
      })
    )
  })

  it('does not call the API when nothing changed', async () => {
    const error = vi.spyOn(toast, 'error')
    renderDialog(existing)

    submit()

    await waitFor(() => expect(error).toHaveBeenCalledWith('No changes to apply'))
    expect(updateCredential).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })
})

it('closes from the cancel button', () => {
  renderDialog()

  fireEvent.click(screen.getByText('Cancel'))

  expect(onClose).toHaveBeenCalled()
})
