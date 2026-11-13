import { useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'
import { useSelector } from 'react-redux'

import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Box,
  Button,
  Card,
  CircularProgress,
  DialogActions,
  DialogContent,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import Dialog from '@mui/material/Dialog'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import 'react-widgets/styles.css'
import { selectFilteredOrganizationNameObjects, selectSuperUser } from '../../generalSlices/userSlice'
import { RootState } from '../../store'
import { ErrorMessageText } from '../../styles/components/Messages'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import '../../styles/fonts/museo-slab.css'
import '../adminRsuTab/Admin.css'
import { useCreateUserMutation, useGetUserAllowedSelectionsQuery } from '../api/userApiSlice'

const AdminAddUser = () => {
  const navigate = useNavigate()
  const isSuperUser = useSelector(selectSuperUser)
  const [selectedOrganizations, setSelectedOrganizations] = useState<UserOrganizationOptionalOrg[]>([
    { organization: undefined, role: 'USER' },
  ])
  const [open, setOpen] = useState(true)

  // RTK Query hooks
  const { data: allowedSelections, isLoading: isLoadingData } = useGetUserAllowedSelectionsQuery()
  const [createUser, { isLoading: isCreating }] = useCreateUserMutation()
  const allowedOrganizations = useSelector((state: RootState) =>
    selectFilteredOrganizationNameObjects(state, allowedSelections?.organizations ?? [])
  )

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AdminUserCreationBody>({
    defaultValues: {
      super_user: false,
    },
  })

  const handleClose = () => {
    setOpen(false)
    navigate('/dashboard/admin/users')
  }

  const handleAddOrganization = () => {
    setSelectedOrganizations([...selectedOrganizations, { organization: undefined, role: 'USER' }])
  }

  const handleRemoveOrganization = (index: number) => {
    // Prevent removing if it's the last one
    if (selectedOrganizations.length === 1) {
      toast.error('At least one organization is required')
      return
    }
    setSelectedOrganizations(selectedOrganizations.filter((_, i) => i !== index))
  }

  const handleOrganizationChange = (index: number, field: keyof UserOrganization, value: number | UserRole) => {
    const updated = [...selectedOrganizations]

    // If changing organization, check for duplicates
    if (field === 'organization') {
      const isDuplicate = updated.some((org, i) => i !== index && org.organization === value)
      if (isDuplicate) {
        toast.error('This organization has already been added')
        return
      }
      updated[index] = { ...updated[index], organization: value as number }
    } else {
      updated[index] = { ...updated[index], role: value as UserRole }
    }

    setSelectedOrganizations(updated)
  }

  // Get available organizations excluding already selected ones
  const getAvailableOrganizations = (currentIndex: number) => {
    const selectedOrgIds = new Set(
      selectedOrganizations
        .map((org, index) => (index !== currentIndex ? org.organization : null))
        .filter((org) => org !== null && org !== undefined)
    )
    return allowedOrganizations?.filter((org) => !selectedOrgIds.has(org.id)) || []
  }

  const hasOrganizations =
    selectedOrganizations.length > 0 && selectedOrganizations.some((org) => org.organization !== undefined)

  const checkForm = (): boolean =>
    selectedOrganizations.length > 0 && selectedOrganizations.every((org) => org.organization !== undefined)

  const handleFormSubmit = async (data: AdminUserCreationBody) => {
    if (!checkForm()) {
      toast.error('Must select at least one organization')
      return
    }

    try {
      const requestBody = {
        ...data,
        super_user: isSuperUser ? Boolean(data.super_user) : false,
        organizations: selectedOrganizations.map((org) => ({
          organization: org.organization as number,
          role: org.role,
        })),
      }

      await createUser(requestBody).unwrap()
      toast.success('User Created Successfully')

      // Reset form
      reset()
      setSelectedOrganizations([{ organization: undefined, role: 'USER' }])

      handleClose()
    } catch (error: any) {
      console.log('Error creating User:', error)
      toast.error(
        'Failed to add User due to error: ' +
          (error?.data?.message || error?.data?.detail || error?.message || 'Unknown error')
      )
    }
  }

  if (isLoadingData) {
    return (
      <Dialog open={open}>
        <DialogContent sx={{ width: '600px', padding: '40px' }}>
          <Box display="flex" justifyContent="center" alignItems="center">
            <CircularProgress />
          </Box>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <Dialog open={open}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader
          onClick={() => {
            setOpen(false)
            navigate('..')
          }}
          title="Add User"
        />
        <Form id="add-user-form" onSubmit={handleSubmit(handleFormSubmit)}>
          <Form.Group controlId="email">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Email"
                placeholder="Enter User Email"
                color="info"
                variant="outlined"
                required
                {...register('email', {
                  required: 'Please enter user email',
                  pattern: {
                    value: /^[^@ ]+@[^@ ]+\.[^@ .]{2,}$/,
                    message: 'Please enter a valid email',
                  },
                })}
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

          <Form.Group controlId="first_name">
            <FormControl fullWidth margin="normal">
              <TextField
                label="First Name"
                placeholder="Enter First Name"
                color="info"
                variant="outlined"
                required
                {...register('first_name', {
                  required: "Please enter user's first name",
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.first_name && (
                <p className="errorMsg" role="alert">
                  {errors.first_name.message}
                </p>
              )}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="last_name">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Last Name"
                placeholder="Enter Last Name"
                color="info"
                variant="outlined"
                required
                {...register('last_name', {
                  required: "Please enter user's last name",
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.last_name && (
                <p className="errorMsg" role="alert">
                  {errors.last_name.message}
                </p>
              )}
            </FormControl>
          </Form.Group>

          {isSuperUser && (
            <Form.Group controlId="super_user">
              <Form.Check label=" Super User" type="switch" {...register('super_user')} />
            </Form.Group>
          )}

          <Box sx={{ mt: 3, mb: 2 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="subtitle1" fontWeight="bold">
                Organizations & Roles
              </Typography>
              <Button
                variant="outlined"
                size="small"
                startIcon={<AddIcon />}
                onClick={handleAddOrganization}
                className="museo-slab capital-case"
                disabled={
                  !!allowedSelections?.organizations &&
                  selectedOrganizations.length >= allowedSelections.organizations.length
                }
              >
                Add Organization
              </Button>
            </Box>

            {selectedOrganizations.map((orgRole, index) => (
              <Card key={index} sx={{ mb: 2, p: 2, position: 'relative' }}>
                <Box display="flex" gap={2} flexDirection={{ xs: 'column', sm: 'row' }} sx={{ pr: 5 }}>
                  <FormControl fullWidth>
                    <InputLabel id={`organization-label-${index}`}>Organization</InputLabel>
                    <Select
                      labelId={`organization-label-${index}`}
                      value={orgRole.organization ?? ''}
                      label="Organization"
                      onChange={(e) => handleOrganizationChange(index, 'organization', e.target.value as number)}
                      required
                    >
                      {getAvailableOrganizations(index).map((org) => (
                        <MenuItem key={org.id} value={org.id}>
                          {org.name}
                        </MenuItem>
                      ))}
                      {/* Show currently selected org even if it would be filtered out */}
                      {orgRole.organization !== undefined &&
                        !getAvailableOrganizations(index).some((org) => org.id === orgRole.organization) && (
                          <MenuItem key={orgRole.organization} value={orgRole.organization}>
                            {allowedOrganizations?.find((org) => org.id === orgRole.organization)?.name ||
                              orgRole.organization}
                          </MenuItem>
                        )}
                    </Select>
                  </FormControl>

                  <FormControl fullWidth>
                    <InputLabel>Role</InputLabel>
                    <Select
                      value={orgRole.role}
                      label="Role"
                      onChange={(e) => handleOrganizationChange(index, 'role', e.target.value as UserRole)}
                      required
                      disabled={orgRole.organization === undefined}
                    >
                      {allowedSelections?.roles?.map((role) => (
                        <MenuItem key={role.toUpperCase()} value={role.toUpperCase()}>
                          {role}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Box>

                {selectedOrganizations.length > 1 && (
                  <IconButton
                    aria-label="delete"
                    size="small"
                    onClick={() => handleRemoveOrganization(index)}
                    sx={{
                      position: 'absolute',
                      top: 8,
                      right: 8,
                      color: 'error.main',
                      '&:hover': {
                        backgroundColor: 'error.lighter',
                      },
                    }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                )}
              </Card>
            ))}

            {!hasOrganizations && (
              <ErrorMessageText role="alert">Must select at least one organization</ErrorMessageText>
            )}
          </Box>
        </Form>
      </DialogContent>
      <DialogActions sx={{ padding: '20px', mt: 1 }}>
        <Button
          onClick={handleClose}
          variant="outlined"
          color="info"
          style={{ position: 'absolute', bottom: 10, left: 10 }}
          className="museo-slab capital-case"
          disabled={isCreating}
        >
          Cancel
        </Button>
        <Button
          form="add-user-form"
          type="submit"
          variant="contained"
          style={{ position: 'absolute', bottom: 10, right: 10 }}
          className="museo-slab capital-case"
          disabled={isCreating || !hasOrganizations}
        >
          {isCreating ? 'Adding...' : 'Add User'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default AdminAddUser
