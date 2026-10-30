import { ErrorMessage } from '@hookform/error-message'
import { useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import toast from 'react-hot-toast'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ErrorMessageText } from '../../styles/components/Messages'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import '../../styles/fonts/museo-slab.css'
import {
  useGetIntersectionAllowedSelectionsQuery,
  useGetIntersectionQuery,
  usePatchIntersectionMutation,
} from '../api/adminIntersectionApiSlice'

export type AdminEditIntersectionFormType = {
  orig_intersection_id?: string
  intersection_id: string
  ref_pt?: {
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
  organizations: number[]
  rsus: string[]
}

const AdminEditIntersection = () => {
  const navigate = useNavigate()
  const { intersectionId } = useParams<{ intersectionId: string }>()

  const { data: intersectionInfo, isLoading: isLoadingIntersection } = useGetIntersectionQuery(intersectionId!)
  const { data: allowedSelections, isLoading: isLoadingAllowedSelections } = useGetIntersectionAllowedSelectionsQuery()
  const [patchIntersection, { isLoading: isPatchingIntersection }] = usePatchIntersectionMutation()

  const [open, setOpen] = useState(true)
  const [submitAttempt, setSubmitAttempt] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<AdminEditIntersectionFormType>({
    values: intersectionInfo
      ? {
          orig_intersection_id: intersectionInfo.intersection_id,
          intersection_id: intersectionInfo.intersection_id,
          ref_pt: {
            latitude: intersectionInfo.ref_pt?.latitude ?? '',
            longitude: intersectionInfo.ref_pt?.longitude ?? '',
          },
          bbox: {
            latitude1: intersectionInfo.bbox?.latitude1 ?? '',
            longitude1: intersectionInfo.bbox?.longitude1 ?? '',
            latitude2: intersectionInfo.bbox?.latitude2 ?? '',
            longitude2: intersectionInfo.bbox?.longitude2 ?? '',
          },
          intersection_name: intersectionInfo.intersection_name,
          origin_ip: intersectionInfo.origin_ip,
          organizations: intersectionInfo.organizations,
          rsus: intersectionInfo.rsus,
        }
      : undefined,
  })
  const watchedOrganizations = watch('organizations')
  const watchedRsus = watch('rsus')

  const onSubmit = async (data: AdminEditIntersectionFormType) => {
    setSubmitAttempt(true)
    if (data.organizations.length === 0) {
      toast.error('Must select at least one organization')
      return
    }
    try {
      console.log('Intersection Ref Pt', data.ref_pt, intersectionInfo?.ref_pt)
      const refPt =
        data.ref_pt &&
        data.ref_pt !== intersectionInfo?.ref_pt &&
        data.ref_pt.latitude &&
        data.ref_pt.latitude != intersectionInfo?.ref_pt?.latitude &&
        data.ref_pt.longitude &&
        data.ref_pt.longitude != intersectionInfo?.ref_pt?.longitude
          ? data.ref_pt
          : undefined
      const bbox =
        data.bbox &&
        data.bbox !== intersectionInfo?.bbox &&
        data.bbox.latitude1 &&
        data.bbox.latitude1 != intersectionInfo?.bbox?.latitude1 &&
        data.bbox.longitude1 &&
        data.bbox.longitude1 != intersectionInfo?.bbox?.longitude1 &&
        data.bbox.latitude2 &&
        data.bbox.latitude2 != intersectionInfo?.bbox?.latitude2 &&
        data.bbox.longitude2 &&
        data.bbox.longitude2 != intersectionInfo?.bbox?.longitude2
          ? data.bbox
          : undefined

      const patchData = {
        intersection_id: data.intersection_id !== data.orig_intersection_id ? data.intersection_id : undefined,
        ref_pt: refPt,
        bbox: bbox,
        intersection_name:
          data.intersection_name !== intersectionInfo?.intersection_name ? data.intersection_name : undefined,
        origin_ip: data.origin_ip !== intersectionInfo?.origin_ip ? data.origin_ip : undefined,
        organizations_to_add: data.organizations
          .filter((org) => !intersectionInfo?.organizations.some((o) => o === org))
          .map((org) => org),
        organizations_to_remove: intersectionInfo?.organizations
          .filter((org) => !data.organizations.some((o) => o === org))
          .map((org) => org),
        rsus_to_add: data.rsus.filter((rsu) => !intersectionInfo?.rsus.some((o) => o === rsu)).map((rsu) => rsu),
        rsus_to_remove: intersectionInfo?.rsus.filter((rsu) => !data.rsus.some((o) => o === rsu)).map((rsu) => rsu),
      }

      // Remove undefined fields
      Object.keys(patchData).forEach((key) => {
        const value = patchData[key as keyof typeof patchData]
        if (value === undefined || (Array.isArray(value) && value.length === 0)) {
          delete patchData[key as keyof typeof patchData]
        }
      })

      await patchIntersection({ intersection_id: data.orig_intersection_id!, patch: patchData }).unwrap()
      toast.success('Intersection updated successfully')
      setOpen(false)
      navigate('/dashboard/admin/intersections')
    } catch (error: any) {
      toast.error(
        'Failed to update intersection: ' +
          (error?.data?.message || error?.message || error?.data?.detail || 'Unknown error')
      )
    }
  }

  const isLoading = isLoadingIntersection || isLoadingAllowedSelections

  return (
    <Dialog open={open}>
      {!isLoading && intersectionInfo && allowedSelections ? (
        <>
          <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
            <SideBarHeader
              onClick={() => {
                setOpen(false)
                navigate('..')
              }}
              title="Edit Intersection"
            />
            <Form
              id="edit-intersection-form"
              onSubmit={handleSubmit(onSubmit)}
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
                  <ErrorMessage
                    errors={errors}
                    name="intersection_id"
                    render={({ message }) => (
                      <p className="errorMsg" role="alert">
                        {' '}
                        {message}{' '}
                      </p>
                    )}
                  />
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
                  <ErrorMessage
                    errors={errors}
                    name="ref_pt.latitude"
                    render={({ message }) => (
                      <p className="errorMsg" role="alert">
                        {' '}
                        {message}{' '}
                      </p>
                    )}
                  />
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
                        value:
                          /^(\+|-)?(?:180(?:(?:\.0{1,8})?)|(?:[0-9]|[1-9][0-9]|1[0-7][0-9])(?:(?:\.[0-9]{1,8})?))$/,
                        message: 'Please enter a valid longitude, in degrees',
                      },
                    })}
                    slotProps={{
                      inputLabel: {
                        shrink: true,
                      },
                    }}
                  />
                  <ErrorMessage
                    errors={errors}
                    name="ref_pt.longitude"
                    render={({ message }) => (
                      <p className="errorMsg" role="alert">
                        {' '}
                        {message}{' '}
                      </p>
                    )}
                  />
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
                  <ErrorMessage
                    errors={errors}
                    name="origin_ip"
                    render={({ message }) => (
                      <p className="errorMsg" role="alert">
                        {' '}
                        {message}{' '}
                      </p>
                    )}
                  />
                </FormControl>
              </Form.Group>

              <Form.Group controlId="organizations">
                <FormControl fullWidth margin="normal">
                  <InputLabel htmlFor="organizations" required>
                    Organizations
                  </InputLabel>
                  <Select
                    id="organizations"
                    label="Organizations"
                    multiple
                    required
                    value={watchedOrganizations || []}
                    {...register('organizations', { required: true })}
                    onChange={(event) => {
                      const value = event.target.value as number[]
                      setValue('organizations', value)
                    }}
                  >
                    {allowedSelections.organizations?.map((org) => (
                      <MenuItem key={org} value={org}>
                        {org}
                      </MenuItem>
                    ))}
                  </Select>
                  {watchedOrganizations?.length === 0 && submitAttempt && (
                    <ErrorMessageText role="alert">Must select an organization</ErrorMessageText>
                  )}
                </FormControl>
              </Form.Group>

              <Form.Group controlId="rsus">
                <FormControl fullWidth margin="normal">
                  <InputLabel htmlFor="rsus" required>
                    RSUs
                  </InputLabel>
                  <Select
                    id="rsus"
                    label="RSUs"
                    multiple
                    required
                    value={watchedRsus || []}
                    {...register('rsus', { required: true })}
                    onChange={(event) => {
                      const value = event.target.value as string[]
                      setValue('rsus', value)
                    }}
                  >
                    {allowedSelections.rsus?.map((rsu) => (
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
              onClick={() => {
                setOpen(false)
                navigate('/dashboard/admin/intersections')
              }}
              variant="outlined"
              color="info"
              style={{ position: 'absolute', bottom: 10, left: 10 }}
              className="museo-slab capital-case"
            >
              Cancel
            </Button>
            <Button
              form="edit-intersection-form"
              type="submit"
              variant="contained"
              disabled={isPatchingIntersection}
              style={{ position: 'absolute', bottom: 10, right: 10 }}
              className="museo-slab capital-case"
            >
              {isPatchingIntersection ? 'Saving...' : 'Apply Changes'}
            </Button>
          </DialogActions>
        </>
      ) : isLoading ? (
        <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
          <Typography variant={'h4'}>Loading...</Typography>
        </DialogContent>
      ) : (
        <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
          <Typography variant={'h4'}>
            Unknown Intersection ID. Either this intersection does not exist, or you do not have access to it.{' '}
            <Link to="../">Intersections</Link>
          </Typography>
        </DialogContent>
      )}
    </Dialog>
  )
}

export default AdminEditIntersection
