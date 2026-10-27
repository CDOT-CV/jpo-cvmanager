import { ThunkDispatch, AnyAction } from '@reduxjs/toolkit'
import { confirmAlert } from 'react-confirm-alert'
import 'react-confirm-alert/src/react-confirm-alert.css'
import { useDispatch, useSelector } from 'react-redux'
import { RootState } from '../store'

import {
  selectRebootChangeSuccess,
  selectRebootRequestedIp,

  // Actions
  rebootRsu,
} from '../generalSlices/configSlice'

import { selectRsuIpv4 } from '../generalSlices/rsuSlice'

import './css/SnmpwalkMenu.css'
import { Button, Typography, useTheme } from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import toast from 'react-hot-toast'

const RsuRebootMenu = () => {
  const theme = useTheme()
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const changeSuccess = useSelector(selectRebootChangeSuccess)
  const requestedIp = useSelector(selectRebootRequestedIp)

  const rsuIp = useSelector(selectRsuIpv4)

  const options = {
    title: 'RSU Reboot',
    message: 'Are you sure you want to perform a reboot?',
    buttons: [
      {
        label: 'Yes',
        onClick: () => {
          dispatch(rebootRsu(rsuIp)).then((result) => {
            if (rebootRsu.rejected.match(result)) {
              toast.error(result.error.message || 'Reboot request was not accepted')
            }
          })
        },
      },
      {
        label: 'No',
        onClick: () => {},
      },
    ],
    childrenElement: () => <div />,
    closeOnEscape: true,
    closeOnClickOutside: true,
    keyCodeForClose: [8, 32],
    willUnmount: () => {},
    afterClose: () => {},
    onClickOutside: () => {},
    onKeypressEscape: () => {},
  }

  return (
    <div>
      {changeSuccess && requestedIp === rsuIp ? (
        <div>
          <Typography color={theme.palette.success.light} role="status">
            Reboot requested. The RSU may be offline briefly.
          </Typography>
        </div>
      ) : (
        <Typography>Warning: This action could result in taking the RSU offline</Typography>
      )}
      <Button
        className="museo-slab capital-case"
        variant="outlined"
        size="medium"
        color="info"
        startIcon={<RefreshIcon />}
        onClick={() => confirmAlert(options)}
        sx={{ mt: '10px', mb: '10px' }}
      >
        Reboot RSU
      </Button>
    </div>
  )
}

export default RsuRebootMenu
