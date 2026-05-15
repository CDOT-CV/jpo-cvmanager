import { useEffect, useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm, useFieldArray } from 'react-hook-form'
import { useSelector } from 'react-redux'
import { selectSuperUser } from '../../generalSlices/userSlice'

import '../adminRsuTab/Admin.css'
import 'react-widgets/styles.css'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  DialogActions,
  DialogContent,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
  IconButton,
  Box,
  Card,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import AddIcon from '@mui/icons-material/Add'
import Dialog from '@mui/material/Dialog'
import toast from 'react-hot-toast'
import { ErrorMessageText } from '../../styles/components/Messages'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { useGetUserAllowedSelectionsQuery, useGetUserQuery, usePatchUserMutation } from '../api/userApiSlice'

interface UserFormData {
  orig_email: string
  email: string
  first_name: string
  last_name: string
  super_user: boolean
  organizations: UserOrganization[]
}

const AdminEditUser = () => {
  const navigate = useNavigate()
  const { email } = useParams<{ email: string }>()

  const { data: userInfo, isLoading: isLoadingUser } = useGetUserQuery(email!)
  const { data: userAllowedSelections, isLoading: isLoadingAllowedSelections } = useGetUserAllowedSelectionsQuery()
  const [patchUser, { isLoading: isPatchingUser }] = usePatchUserMutation()
  const isSuperUser = useSelector(selectSuperUser)

  const [open, setOpen] = useState(true)

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
    reset,
  } = useForm<UserFormData>({
    defaultValues: {
      orig_email: '',
      email: '',
      first_name: '',
      last_name: '',
      super_user: false,
      organizations: [],
    },
  })

  const { fields, append, remove, update } = useFieldArray({
    control,
    name: 'organizations',
  })

  // Initialize form when user data loads
  useEffect(() => {
    if (userInfo) {
      reset({
        orig_email: userInfo.email,
        email: userInfo.email,
        first_name: userInfo.first_name,
        last_name: userInfo.last_name,
        super_user: userInfo.super_user,
        organizations: userInfo.organizations,
      })
    }
  }, [userInfo, reset])

  const onSubmit = async (data: UserFormData) => {
    if (data.organizations.length === 0) {
      toast.error('Must select at least one organization')
      return
    }

    try {
      const patchData = {
        email: data.email !== data.orig_email ? data.email : undefined,
        first_name: data.first_name !== userInfo?.first_name ? data.first_name : undefined,
        last_name: data.last_name !== userInfo?.last_name ? data.last_name : undefined,
        super_user: data.super_user !== userInfo?.super_user ? data.super_user : undefined,
        organizations_to_add: data.organizations
          .filter((org) => !userInfo?.organizations.some((o) => o.organization === org.organization))
          .map((org) => ({ organization: org.organization, role: org.role })),
        organizations_to_remove: userInfo?.organizations
          .filter((org) => !data.organizations.some((o) => o.organization === org.organization))
          .map((org) => org.organization),
        organizations_to_modify: data.organizations
          .filter((org) => {
            const originalOrg = userInfo?.organizations.find((o) => o.organization === org.organization)
            return originalOrg && originalOrg.role !== org.role
          })
          .map((org) => ({ organization: org.organization, role: org.role })),
      }

      // Remove undefined fields
      Object.keys(patchData).forEach((key) => {
        const value = patchData[key as keyof typeof patchData]
        if (value === undefined || (Array.isArray(value) && value.length === 0)) {
          delete patchData[key as keyof typeof patchData]
        }
      })

      await patchUser({ email: data.orig_email, patch: patchData }).unwrap()
      toast.success('User updated successfully')
      setOpen(false)
      navigate('/dashboard/admin/users')
    } catch (error: any) {
      toast.error(
        'Failed to update user: ' + (error?.data?.message || error?.message || error?.data?.detail || 'Unknown error')
      )
    }
  }

  const handleAddOrganization = () => {
    append({ organization: '', role: 'USER' })
  }

  const handleRemoveOrganization = (index: number) => {
    if (fields.length === 1) {
      toast.error('At least one organization is required')
      return
    }
    remove(index)
  }

  const handleOrganizationChange = (index: number, fieldKey: keyof UserOrganization, value: string) => {
    const current = fields[index]
    if (fieldKey === 'organization') {
      const isDuplicate = fields.some((f, i) => i !== index && f.organization === value)
      if (isDuplicate) {
        toast.error('This organization has already been added')
        return
      }
      update(index, { ...current, organization: value })
    } else {
      update(index, { ...current, role: value as UserRole })
    }
  }

  const getAvailableOrganizations = (currentIndex: number) => {
    const selectedOrgNames = fields
      .map((field, index) => (index !== currentIndex ? field.organization : null))
      .filter((org) => org !== null && org !== '')
    return userAllowedSelections?.organizations?.filter((org) => !selectedOrgNames.includes(org)) || []
  }

  const isLoading = isLoadingUser || isLoadingAllowedSelections
  const hasOrganizations = fields.length > 0

  const handleClose = () => {
    setOpen(false)
    navigate('/dashboard/admin/users')
  }

  return (
    <Dialog open={open} onClose={handleClose}>
      {!isLoading && userInfo && userAllowedSelections ? (
        <>
          <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
            <SideBarHeader onClick={handleClose} title="Edit User" />
            <Form id="edit-user-form" onSubmit={handleSubmit(onSubmit)}>
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
                  {errors.email && <ErrorMessageText role="alert">{errors.email.message}</ErrorMessageText>}
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
                  {errors.first_name && <ErrorMessageText role="alert">{errors.first_name.message}</ErrorMessageText>}
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
                  {errors.last_name && <ErrorMessageText role="alert">{errors.last_name.message}</ErrorMessageText>}
                </FormControl>
              </Form.Group>

              {isSuperUser && (
                <Form.Group controlId="super_user">
                  <Form.Check label=" Super User" className="trebuchet" type="switch" {...register('super_user')} />
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
                      !!userAllowedSelections?.organizations &&
                      fields.length >= userAllowedSelections.organizations.length
                    }
                  >
                    Add Organization
                  </Button>
                </Box>

                {fields.map((field, index) => (
                  <Card key={field.id} sx={{ mb: 2, p: 2, position: 'relative' }}>
                    <Box display="flex" gap={2} flexDirection={{ xs: 'column', sm: 'row' }} sx={{ pr: 5 }}>
                      <FormControl fullWidth>
                        <InputLabel>Organization</InputLabel>
                        <Select
                          value={field.organization}
                          label="Organization"
                          onChange={(e) => handleOrganizationChange(index, 'organization', e.target.value)}
                          required
                        >
                          {getAvailableOrganizations(index).map((org) => (
                            <MenuItem key={org} value={org}>
                              {org}
                            </MenuItem>
                          ))}
                          {field.organization && !getAvailableOrganizations(index).includes(field.organization) && (
                            <MenuItem key={field.organization} value={field.organization}>
                              {field.organization}
                            </MenuItem>
                          )}
                        </Select>
                      </FormControl>

                      <FormControl fullWidth>
                        <InputLabel>Role</InputLabel>
                        <Select
                          value={field.role}
                          label="Role"
                          onChange={(e) => handleOrganizationChange(index, 'role', e.target.value)}
                          required
                          disabled={!field.organization}
                        >
                          {userAllowedSelections.roles?.map((role) => (
                            <MenuItem key={role} value={role}>
                              {role}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Box>

                    {fields.length > 1 && (
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
              form="edit-user-form"
              type="submit"
              variant="contained"
              disabled={isPatchingUser || !hasOrganizations}
              style={{ position: 'absolute', bottom: 10, right: 10 }}
              className="museo-slab capital-case"
            >
              {isPatchingUser ? 'Saving...' : 'Apply Changes'}
            </Button>
          </DialogActions>
        </>
      ) : (
        !isLoading && (
          <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
            <Typography variant={'h4'}>
              Unknown email address. Either this user does not exist, or you do not have permissions to view them.{' '}
              <Link to="../">Users</Link>
            </Typography>
          </DialogContent>
        )
      )}
    </Dialog>
  )
}

export default AdminEditUser
