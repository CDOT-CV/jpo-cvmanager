import { useEffect, useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'
import toast from 'react-hot-toast'

import '../adminRsuTab/Admin.css'
import 'react-widgets/styles.css'
import '../../styles/fonts/museo-slab.css'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { Button, DialogActions, DialogContent, FormControl, TextField, Typography } from '@mui/material'
import Dialog from '@mui/material/Dialog'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { useGetOrganizationsQuery, usePatchOrganizationMutation } from '../api/organizationApiSlice'

interface OrganizationFormData {
  id: number
  name: string
  email: string
}

const AdminEditOrganization = () => {
  const { orgName } = useParams<{ orgName: string }>()
  const navigate = useNavigate()

  const { data: organizations, isLoading } = useGetOrganizationsQuery()
  const [patchOrganization, { isLoading: isPatching }] = usePatchOrganizationMutation()

  const orgInfo = organizations?.find((o) => o.name === orgName)

  const [open, setOpen] = useState(true)

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<OrganizationFormData>({
    defaultValues: {
      id: -1,
      name: '',
      email: '',
    },
  })

  useEffect(() => {
    if (orgInfo) {
      reset({
        id: orgInfo.id,
        name: orgInfo.name,
        email: orgInfo.email ?? '',
      })
    }
  }, [orgInfo, reset])

  const handleClose = () => {
    setOpen(false)
    navigate('/dashboard/admin/organizations')
  }

  const onSubmit = async (data: OrganizationFormData) => {
    try {
      await patchOrganization({
        id: data.id,
        name: data.name,
        email: data.email,
      }).unwrap()
      toast.success('Organization updated successfully')
      setOpen(false)
      navigate('/dashboard/admin/organizations')
    } catch (error: any) {
      toast.error(
        'Failed to update organization: ' +
          (error?.data?.message || error?.message || error?.data?.detail || 'Unknown error')
      )
    }
  }

  return (
    <Dialog open={open} onClose={handleClose}>
      {!isLoading && orgInfo ? (
        <>
          <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
            <SideBarHeader onClick={handleClose} title="Edit Organization" />
            <Form id="admin-edit-org" onSubmit={handleSubmit(onSubmit)}>
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
                    {...register('email')}
                    slotProps={{
                      inputLabel: {
                        shrink: true,
                      },
                    }}
                  />
                </FormControl>
              </Form.Group>
            </Form>
          </DialogContent>
          <DialogActions sx={{ padding: '20px' }}>
            <Button
              onClick={handleClose}
              variant="outlined"
              color="info"
              style={{ position: 'absolute', bottom: 10, left: 10 }}
              className="museo-slab capital-case"
            >
              Cancel
            </Button>
            <Button
              form="admin-edit-org"
              type="submit"
              variant="contained"
              disabled={isPatching}
              style={{ position: 'absolute', bottom: 10, right: 10 }}
              className="museo-slab capital-case"
            >
              {isPatching ? 'Saving...' : 'Apply Changes'}
            </Button>
          </DialogActions>
        </>
      ) : (
        !isLoading && (
          <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
            <Typography variant={'h4'}>
              Unknown organization. Either this organization does not exist, or you do not have access to it.{' '}
              <Link to="../">Organizations</Link>
            </Typography>
          </DialogContent>
        )
      )}
    </Dialog>
  )
}

export default AdminEditOrganization
