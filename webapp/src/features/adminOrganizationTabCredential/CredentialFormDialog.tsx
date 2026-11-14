import { useForm } from 'react-hook-form'
import { Button, Dialog, DialogActions, DialogContent, FormControl, TextField } from '@mui/material'
import toast from 'react-hot-toast'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import {
  CredentialSummary,
  CredentialType,
  useCreateCredentialMutation,
  useUpdateCredentialMutation,
} from '../api/credentialApiSlice'

import '../adminRsuTab/Admin.css'
import '../../styles/fonts/museo-slab.css'

// Column length of nickname, username and password in the credential tables
const MAX_FIELD_LENGTH = 128

export type CredentialFormValues = {
  nickname: string
  username: string
  password: string
}

interface CredentialFormDialogProps {
  type: CredentialType
  /** Shown in titles and messages, e.g. "SSH" or "SNMP". */
  typeLabel: string
  /** Organization that will own a newly created credential. */
  organization: string
  /** The credential being edited. Omit to add a new one. */
  credential?: CredentialSummary
  onClose: () => void
}

export const getCredentialErrorMessage = (error: any): string =>
  error?.data?.detail || error?.data?.message || error?.message || 'Unknown error'

const CredentialFormDialog = (props: CredentialFormDialogProps) => {
  const { type, typeLabel, organization, credential, onClose } = props
  const isEdit = credential !== undefined
  const formId = `${type}-credential-form`

  const [createCredential, { isLoading: isCreating }] = useCreateCredentialMutation()
  const [updateCredential, { isLoading: isUpdating }] = useUpdateCredentialMutation()
  const isSubmitting = isCreating || isUpdating

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CredentialFormValues>({
    defaultValues: {
      nickname: credential?.nickname ?? '',
      username: credential?.username ?? '',
      password: '',
    },
  })

  const maxLength = (fieldLabel: string) => ({
    value: MAX_FIELD_LENGTH,
    message: `${fieldLabel} must be ${MAX_FIELD_LENGTH} characters or fewer`,
  })

  const onSubmit = async (data: CredentialFormValues) => {
    const username = data.username.trim()
    try {
      if (isEdit) {
        const usernameChanged = username !== credential.username
        if (!usernameChanged && !data.password) {
          toast.error('No changes to apply')
          return
        }
        await updateCredential({
          type,
          nickname: credential.nickname,
          username: usernameChanged ? username : undefined,
          password: data.password || undefined,
        }).unwrap()
        toast.success(`${typeLabel} Credential Updated Successfully`)
      } else {
        await createCredential({
          type,
          nickname: data.nickname.trim(),
          username,
          password: data.password,
          organization,
        }).unwrap()
        toast.success(`${typeLabel} Credential Created Successfully`)
      }
      onClose()
    } catch (error: any) {
      toast.error(
        `Failed to ${isEdit ? 'update' : 'create'} ${typeLabel} credential due to error: ${getCredentialErrorMessage(error)}`
      )
    }
  }

  return (
    <Dialog open onClose={() => !isSubmitting && onClose()}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader onClick={onClose} title={`${isEdit ? 'Edit' : 'Add'} ${typeLabel} Credential`} />
        <form
          id={formId}
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          style={{ fontFamily: '"museo-slab", Arial, Helvetica, sans-serif' }}
        >
          <FormControl fullWidth margin="normal">
            <TextField
              label="Nickname"
              placeholder="Enter Credential Nickname"
              color="info"
              variant="outlined"
              required
              disabled={isEdit}
              {...register('nickname', {
                required: 'Please enter a nickname',
                validate: (value) => value.trim().length > 0 || 'Please enter a nickname',
                maxLength: maxLength('Nickname'),
              })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            {errors.nickname && (
              <p className="errorMsg" role="alert">
                {errors.nickname.message}
              </p>
            )}
          </FormControl>
          <FormControl fullWidth margin="normal">
            <TextField
              label="Username"
              placeholder="Enter Username"
              color="info"
              variant="outlined"
              required
              autoComplete="off"
              {...register('username', {
                required: 'Please enter a username',
                validate: (value) => value.trim().length > 0 || 'Please enter a username',
                maxLength: maxLength('Username'),
              })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            {errors.username && (
              <p className="errorMsg" role="alert">
                {errors.username.message}
              </p>
            )}
          </FormControl>
          <FormControl fullWidth margin="normal">
            <TextField
              label="Password"
              placeholder={isEdit ? 'Leave blank to keep the current password' : 'Enter Password'}
              type="password"
              color="info"
              variant="outlined"
              required={!isEdit}
              autoComplete="new-password"
              {...register('password', {
                required: isEdit ? false : 'Please enter a password',
                maxLength: maxLength('Password'),
              })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            {errors.password && (
              <p className="errorMsg" role="alert">
                {errors.password.message}
              </p>
            )}
          </FormControl>
        </form>
      </DialogContent>
      <DialogActions sx={{ padding: '20px', mt: 1 }}>
        <Button
          onClick={onClose}
          variant="outlined"
          color="info"
          style={{ position: 'absolute', bottom: 10, left: 10 }}
          className="museo-slab capital-case"
        >
          Cancel
        </Button>
        <Button
          form={formId}
          type="submit"
          variant="contained"
          disabled={isSubmitting}
          style={{ position: 'absolute', bottom: 10, right: 10 }}
          className="museo-slab capital-case"
        >
          {isSubmitting ? 'Saving...' : isEdit ? 'Apply Changes' : 'Add Credential'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default CredentialFormDialog
