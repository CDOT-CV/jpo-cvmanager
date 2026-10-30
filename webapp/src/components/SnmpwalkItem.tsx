import { Box, Button, Typography, useTheme } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { RsuMsgFwdConfigSingle } from '../models/RsuApi'

export type SnmpwalkRowProps = {
  title: string
  value: string | number
}

export const SnmpWalkRow = (props: SnmpwalkRowProps) => {
  return (
    <Typography component="div" sx={{ lineHeight: 1.4, overflowWrap: 'anywhere' }}>
      <Typography component="span" color="textSecondary" sx={{ fontWeight: 'bold' }}>
        {props.title}
      </Typography>
      <Typography component="span" sx={{ ml: 0.5 }}>
        {props.value}
      </Typography>
    </Typography>
  )
}

export type SnmpwalkItemProps = {
  index: string
  content: RsuMsgFwdConfigSingle
  handleDelete?: (row: RsuMsgFwdConfigSingle) => void
}

const SnmpwalkItem = (props: SnmpwalkItemProps) => {
  const theme = useTheme()

  return (
    <Box
      id="snmpitemdiv"
      sx={{
        borderRadius: 2,
        padding: 1,
        marginBottom: 1,
        backgroundColor: theme.palette.background.paper,
        '&:hover': {
          backgroundColor: theme.palette.background.default,
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography component="h4" sx={{ fontWeight: 'bold' }}>
          {props.index}
        </Typography>
        {props.handleDelete && (
          <Button size="small" onClick={() => props.handleDelete(props.content)} startIcon={<DeleteIcon />}>
            Delete
          </Button>
        )}
      </Box>
      <SnmpWalkRow title="Message Type:" value={props.content['Message Type']} />
      <SnmpWalkRow title="Destination IP:" value={props.content['IP']} />
      <SnmpWalkRow title="Port:" value={props.content['Port']} />
      <SnmpWalkRow title="Start:" value={props.content['Start DateTime']} />
      <SnmpWalkRow title="End:" value={props.content['End DateTime']} />
      <SnmpWalkRow title="Security:" value={props.content['Full WSMP']} />
      <SnmpWalkRow title="Active:" value={props.content['Config Active']} />
    </Box>
  )
}

export default SnmpwalkItem
