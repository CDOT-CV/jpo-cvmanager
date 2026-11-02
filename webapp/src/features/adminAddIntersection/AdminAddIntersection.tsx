import { useMemo, useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'

import {
  Button,
  DialogActions,
  DialogContent,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
} from '@mui/material'
import Dialog from '@mui/material/Dialog'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import { AdminIntersectionCreationBody } from '../../models/Intersection'
import { ErrorMessageText } from '../../styles/components/Messages'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import '../../styles/fonts/museo-slab.css'
import {
  useCreateIntersectionMutation,
  useGetIntersectionAllowedSelectionsQuery,
} from '../api/adminIntersectionApiSlice'

export type AdminAddIntersectionForm = {
  intersection_id: string
  ref_pt: {
    latitude: string
    longitude: string
  }
  bbox?: {
    latitude1: string
    longitude1: string
    latitude2: string
    longitude2: string
  }
  intersection_name?: string
  origin_ip?: string
  organizations: string[]
  rsus: string[]
}

const AdminAddIntersection = () => {
  const navigate = useNavigate()
  const [open, setOpen] = useState(true)

  // Local state for form selections
  const [selectedOrganizations, setSelectedOrganizations] = useState<string[]>([])
  const [selectedRsus, setSelectedRsus] = useState<string[]>([])
  const [submitAttempt, setSubmitAttempt] = useState(false)

  // RTK Query hooks
  const { data: allowedSelections } = useGetIntersectionAllowedSelectionsQuery()
  const [createIntersection] = useCreateIntersectionMutation()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AdminAddIntersectionForm>()

  const organizations = allowedSelections?.organizations ?? []
  const rsus = useMemo(
    () => (allowedSelections?.rsus ?? []).map((rsu) => rsu?.replace('/32', '')),
    [allowedSelections]
  )

  const handleClose = () => {
    setOpen(false)
    navigate('/dashboard/admin/intersections')
  }

  const checkForm = (): boolean => selectedOrganizations.length !== 0

  const buildRequestBody = (data: AdminAddIntersectionForm): AdminIntersectionCreationBody => {
    const body: AdminIntersectionCreationBody = {
      ...data,
      intersection_id: Number(data.intersection_id),
      ref_pt: {
        latitude: Number(data.ref_pt.latitude),
        longitude: Number(data.ref_pt.longitude),
      },
      organizations: selectedOrganizations,
      rsus: selectedRsus,
    } as unknown as AdminIntersectionCreationBody
    if (data.bbox?.latitude1 && data.bbox?.longitude1 && data.bbox?.latitude2 && data.bbox?.longitude2) {
      body.bbox = {
        latitude1: Number(data.bbox.latitude1),
        longitude1: Number(data.bbox.longitude1),
        latitude2: Number(data.bbox.latitude2),
        longitude2: Number(data.bbox.longitude2),
      } as any
    }
    return body
  }

  const handleFormSubmit = async (data: AdminAddIntersectionForm) => {
    setSubmitAttempt(true)
    if (!checkForm()) {
      toast.error('Please fill out all required fields')
      return
    }
    const json = buildRequestBody(data)
    try {
      await createIntersection(json).unwrap()
      reset()
      setSelectedOrganizations([])
      setSelectedRsus([])
      setSubmitAttempt(false)
      toast.success('Intersection created successfully')
      handleClose()
    } catch (error) {
      const detail =
        error && typeof error === 'object' && 'data' in error && error.data && typeof error.data === 'object'
          ? (error.data as { detail?: string }).detail
          : undefined
      toast.error(detail ? `Failed to add Intersection: ${detail}` : 'Failed to add Intersection')
    }
  }

  return (
    <Dialog open={open} sx={{ padding: '5px 10px' }}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader onClick={handleClose} title="Add Intersection" />
        <Form
          id="add-intersection-form"
          onSubmit={handleSubmit(handleFormSubmit)}
          style={{ fontFamily: '"museo-slab", Arial, Helvetica, sans-serif' }}
        >
          <Form.Group controlId="intersection_id">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Intersection ID"
                placeholder="Enter Intersection ID"
                color="info"
                variant="outlined"
                required
                {...register('intersection_id', {
                  required: "Please enter the Intersection's numerical ID",
                  pattern: {
                    value: /^[0-9]+$/,
                    message: 'Please enter a valid number',
                  },
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.intersection_id && <p className="errorMsg">{errors.intersection_id.message}</p>}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="ref_pt.latitude">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Latitude"
                placeholder="Enter Reference Point Latitude"
                color="info"
                variant="outlined"
                required
                {...register('ref_pt.latitude', {
                  required: "Please enter the Intersection's reference point Latitude",
                  pattern: {
                    value: /^(\+|-)?(?:90(?:(?:\.0{1,8})?)|(?:[0-9]|[1-8][0-9])(?:(?:\.[0-9]{1,8})?))$/,
                    message: 'Please enter a valid latitude, in degrees',
                  },
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.ref_pt?.latitude && <p className="errorMsg">{errors.ref_pt.latitude.message}</p>}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="ref_pt.longitude">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Longitude"
                placeholder="Enter Reference Point Longitude"
                color="info"
                variant="outlined"
                required
                {...register('ref_pt.longitude', {
                  required: "Please enter the Intersection's reference point Longitude",
                  pattern: {
                    value: /^(\+|-)?(?:180(?:(?:\.0{1,8})?)|(?:[0-9]|[1-9][0-9]|1[0-7][0-9])(?:(?:\.[0-9]{1,8})?))$/,
                    message: 'Please enter a valid longitude, in degrees',
                  },
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.ref_pt?.longitude && <p className="errorMsg">{errors.ref_pt.longitude.message}</p>}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="intersection_name">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Name"
                placeholder="Enter Intersection Name"
                color="info"
                variant="outlined"
                required
                {...register('intersection_name')}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.intersection_name && <p className="errorMsg">{errors.intersection_name.message}</p>}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="origin_ip">
            <FormControl fullWidth margin="normal">
              <TextField
                label="Origin IP"
                placeholder="Enter Origin IP"
                color="info"
                variant="outlined"
                required
                {...register('origin_ip', {
                  pattern: {
                    value:
                      /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
                    message: 'Please enter a valid IP address',
                  },
                })}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              {errors.origin_ip && <p className="errorMsg">{errors.origin_ip.message}</p>}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="organizations">
            <FormControl fullWidth margin="normal">
              <InputLabel>Organizations</InputLabel>
              <Select
                id="organizations"
                label="Organizations"
                multiple
                value={selectedOrganizations}
                onChange={(event) => setSelectedOrganizations(event.target.value as string[])}
              >
                {organizations.map((org) => (
                  <MenuItem key={org} value={org}>
                    {org}
                  </MenuItem>
                ))}
              </Select>
              {selectedOrganizations.length === 0 && submitAttempt && (
                <ErrorMessageText role="alert">Must select an organization</ErrorMessageText>
              )}
            </FormControl>
          </Form.Group>

          <Form.Group controlId="rsus">
            <FormControl fullWidth margin="normal">
              <InputLabel>RSUs</InputLabel>
              <Select
                id="rsus"
                label="RSUs"
                multiple
                value={selectedRsus}
                onChange={(event) => setSelectedRsus(event.target.value as string[])}
              >
                {rsus.map((rsu) => (
                  <MenuItem key={rsu} value={rsu}>
                    {rsu}
                  </MenuItem>
                ))}
              </Select>
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
          Close
        </Button>
        <Button
          form="add-intersection-form"
          type="submit"
          variant="contained"
          style={{ position: 'absolute', bottom: 10, right: 10 }}
          className="museo-slab capital-case"
        >
          Add Intersection
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default AdminAddIntersection
