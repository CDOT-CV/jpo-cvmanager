import { useMemo, useState } from 'react'
import { useSelector } from 'react-redux'
import { Alert, Typography, useTheme } from '@mui/material'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import ModeEditOutline from '@mui/icons-material/ModeEditOutline'
import { Action, Column } from '@material-table/core'
import { confirmAlert } from 'react-confirm-alert'
import toast from 'react-hot-toast'
import AdminTable from '../../components/AdminTable'
import { Options } from '../../components/AdminDeletionOptions'
import { selectAuthLoginData } from '../../generalSlices/userSlice'
import { UserManager } from '../../managers'
import {
  CredentialSummary,
  CredentialType,
  useDeleteCredentialMutation,
  useListCredentialsQuery,
} from '../api/credentialApiSlice'
import CredentialFormDialog, { getCredentialErrorMessage } from './CredentialFormDialog'

import '../adminRsuTab/Admin.css'

const CREDENTIAL_TYPE_LABELS: Record<CredentialType, string> = { rsu: 'SSH', snmp: 'SNMP' }

/**
 * Credentials are only shown to admins of the organization being viewed (or
 * super users), matching what the credentials API allows.
 */
export const canViewOrganizationCredentials = (
  authLoginData: AuthLoginData | null | undefined,
  organizationName: string
): boolean => {
  if (!authLoginData || !organizationName) return false
  if (authLoginData.data?.super_user) return true
  return UserManager.getOrganizationByName(authLoginData, organizationName)?.role?.toUpperCase() === 'ADMIN'
}

type CredentialDialogState = { type: CredentialType; credential?: CredentialSummary }

interface CredentialTableProps {
  type: CredentialType
  selectedOrg: string
  onAdd: () => void
  onEdit: (credential: CredentialSummary) => void
}

const CredentialTable = ({ type, selectedOrg, onAdd, onEdit }: CredentialTableProps) => {
  const theme = useTheme()
  const typeLabel = CREDENTIAL_TYPE_LABELS[type]
  const isSuperUser = !!useSelector(selectAuthLoginData)?.data?.super_user

  // Refetch on mount so the RSUs column reflects RSU edits made elsewhere in the admin pages
  const { data, isFetching, isError, error, refetch } = useListCredentialsQuery(
    { type, organization: selectedOrg },
    { refetchOnMountOrArgChange: true }
  )
  const [deleteCredential] = useDeleteCredentialMutation()

  // The table adds bookkeeping fields to its rows, which the frozen query result does not allow
  const tableData = useMemo(() => (data ?? []).map((credential) => ({ ...credential })), [data])

  const onDelete = async (credential: CredentialSummary) => {
    const loadingToast = toast.loading(`Deleting ${typeLabel} credential ${credential.nickname}...`)
    try {
      await deleteCredential({ type, nickname: credential.nickname }).unwrap()
      toast.success(`${typeLabel} Credential Deleted Successfully`, { id: loadingToast })
    } catch (error: any) {
      toast.error(`Failed to delete ${typeLabel} credential due to error: ${getCredentialErrorMessage(error)}`, {
        id: loadingToast,
      })
    }
  }

  const columns: Column<CredentialSummary>[] = [
    { title: 'Nickname', field: 'nickname' },
    { title: 'Username', field: 'username' },
    { title: 'Owner', field: 'ownerOrganization' },
    {
      // Only RSUs associated with current organization; RSUs of other organizations may also use the credential
      title: 'RSUs',
      field: 'rsuIps',
      sorting: false,
      render: (credential) => (credential.rsuIps.length > 0 ? credential.rsuIps.join(', ') : 'None'),
      customFilterAndSearch: (filter: string, credential) => credential.rsuIps.join(', ').includes(filter),
    },
  ]

  // Only the owning organization manages a credential; other organizations that use it see it read-only.
  // Super users can manage every credential, whichever organization they are viewing.
  const canManage = (credential: CredentialSummary) => isSuperUser || credential.ownerOrganization === selectedOrg

  const actions: (Action<CredentialSummary> | ((credential: CredentialSummary) => Action<CredentialSummary>))[] = [
    (credential) => ({
      icon: () => <ModeEditOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      tooltip: `Edit ${typeLabel} Credential`,
      position: 'row',
      iconProps: { itemType: 'rowAction' },
      hidden: !canManage(credential),
      onClick: () => onEdit(credential),
    }),
    (credential) => ({
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      tooltip: `Delete ${typeLabel} Credential`,
      position: 'row',
      iconProps: { itemType: 'rowAction' },
      hidden: !canManage(credential),
      onClick: () => {
        const buttons = [
          { label: 'Yes', onClick: () => onDelete(credential) },
          { label: 'No', onClick: () => {} },
        ]
        confirmAlert(
          Options(
            `Delete ${typeLabel} Credential`,
            `Are you sure you want to delete the ${typeLabel} credential "${credential.nickname}"?`,
            buttons
          )
        )
      },
    }),
    {
      icon: () => null,
      isFreeAction: true,
      position: 'toolbar',
      iconProps: { title: 'Refresh', color: 'info', itemType: 'outlined' },
      onClick: () => refetch(),
    },
    {
      icon: () => null,
      isFreeAction: true,
      position: 'toolbar',
      iconProps: { title: 'New', color: 'primary', itemType: 'contained' },
      onClick: onAdd,
    },
  ]

  return (
    <>
      {isError && (
        // Without this a failed request is indistinguishable from an organization with no credentials
        <Alert severity="error" sx={{ margin: '8px 16px' }}>
          Failed to load {typeLabel} credentials: {getCredentialErrorMessage(error)}
        </Alert>
      )}
      <AdminTable
        title={`${typeLabel} Credentials`}
        data={tableData}
        columns={columns}
        actions={actions}
        selection={false}
        isLoading={isFetching}
      />
    </>
  )
}

interface AdminOrganizationTabCredentialProps {
  selectedOrg: string
}

const AdminOrganizationTabCredential = ({ selectedOrg }: AdminOrganizationTabCredentialProps) => {
  const authLoginData = useSelector(selectAuthLoginData)
  const [dialog, setDialog] = useState<CredentialDialogState | null>(null)

  if (!canViewOrganizationCredentials(authLoginData, selectedOrg)) {
    return null
  }

  const credentialTypes: CredentialType[] = ['rsu', 'snmp']

  return (
    <div className="accordion">
      <Accordion className="accordion-content" elevation={0}>
        <AccordionSummary
          expandIcon={<ExpandMoreIcon />}
          aria-controls="credentials-content"
          id="credentials-header"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          <Typography variant="h6">RSU Credentials</Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ padding: '8px 0px' }}>
          {credentialTypes.map((type) => (
            <div key={type}>
              <CredentialTable
                type={type}
                selectedOrg={selectedOrg}
                onAdd={() => setDialog({ type })}
                onEdit={(credential) => setDialog({ type, credential })}
              />
            </div>
          ))}
        </AccordionDetails>
      </Accordion>
      {dialog && (
        <CredentialFormDialog
          type={dialog.type}
          typeLabel={CREDENTIAL_TYPE_LABELS[dialog.type]}
          organization={selectedOrg}
          credential={dialog.credential}
          onClose={() => setDialog(null)}
        />
      )}
    </div>
  )
}

export default AdminOrganizationTabCredential
