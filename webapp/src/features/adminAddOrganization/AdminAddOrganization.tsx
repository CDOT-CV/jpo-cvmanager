import { useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'
import { useDispatch } from 'react-redux'
import toast from 'react-hot-toast'

import '../adminRsuTab/Admin.css'

import Dialog from '@mui/material/Dialog'
import { Button, DialogActions, DialogContent, FormControl, TextField } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import '../../styles/fonts/museo-slab.css'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { useCreateOrganizationMutation } from '../api/organizationApiSlice'
import keycloak from '../../keycloak-config'
import { keycloakLogin } from '../../generalSlices/userSlice'
import { AppDispatch } from '../../store'

export type AdminAddOrgForm = {
  name: string
  email: string
}

const AdminAddOrganization = () => {
  const [open, setOpen] = useState(true)
  const [isRefreshingSession, setIsRefreshingSession] = useState(false)
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const [createOrganization, { isLoading: isCreating }] = useCreateOrganizationMutation()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AdminAddOrgForm>()

  const handleClose = () => {
    setOpen(false)
    navigate('/dashboard/admin/organizations')
  }

  const onSubmit = async (data: AdminAddOrgForm) => {
    try {
      await createOrganization(data).unwrap()
      toast.success('Organization Creation is successful.')
      setIsRefreshingSession(true)
      try {
        await keycloak.updateToken(-1)
        if (!keycloak.token) {
          throw new Error('Keycloak did not provide a refreshed access token')
        }
        await dispatch(keycloakLogin(keycloak.token)).unwrap()
      } catch (error) {
        console.error('Organization was created, but the Keycloak session refresh failed:', error)
        toast.error('Organization created, but your session could not be refreshed. Please sign out and sign in again.')
      } finally {
        setIsRefreshingSession(false)
      }
      reset()
      handleClose()
    } catch (error) {
      const message =
        error && typeof error === 'object' && 'data' in error && error.data && typeof error.data === 'object'
          ? (error.data as { detail?: string; message?: string }).detail ??
            (error.data as { message?: string }).message
          : undefined
      toast.error(
        'Failed to add organization due to error: ' +
          (message || (error instanceof Error ? error.message : 'Unknown error'))
      )
    }
  }

  return (
    <Dialog open={open}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader
          onClick={handleClose}
          title="Add Organization"
        />
        <Form
          id="add-organization-form"
          onSubmit={handleSubmit(onSubmit)}
          style={{ fontFamily: '"museo-slab", Arial, Helvetica, sans-serif' }}
        >
          <Form.Group controlId="name">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Organization Name"
                placeholder="Enter Organization Name"
                color="info"
                variant="outlined"
                required
                {...register('name', {
                  required: 'Please enter the organization name',
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.name && (
                <p className="errorMsg" role="alert">
                  {errors.name.message}
                </p>
              )}
            </FormControl>
            <FormControl fullWidth margin="normal">
              <TextField
                label="Organization Email"
                placeholder="Enter Organization Email"
                color="info"
                variant="outlined"
                required
                {...register('email', {
                  required: 'Please enter the organization email',
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: 'Please enter a valid organization email',
                  },
                })}
                type="email"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.email && (
                <p className="errorMsg" role="alert">
                  {errors.email.message}
                </p>
              )}
            </FormControl>
          </Form.Group>
        </Form>
      </DialogContent>
      <DialogActions sx={{ padding: '20px' }}>
        <Button
          onClick={handleClose}
          disabled={isCreating || isRefreshingSession}
          variant="outlined"
          color="info"
          style={{ position: 'absolute', bottom: 10, left: 10 }}
          className="museo-slab capital-case"
        >
          Cancel
        </Button>
        <Button
          form="add-organization-form"
          type="submit"
          disabled={isCreating || isRefreshingSession}
          variant="contained"
          style={{ position: 'absolute', bottom: 10, right: 10 }}
          className="museo-slab capital-case"
        >
          {isRefreshingSession ? 'Refreshing session...' : isCreating ? 'Adding...' : 'Add Organization'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default AdminAddOrganization
