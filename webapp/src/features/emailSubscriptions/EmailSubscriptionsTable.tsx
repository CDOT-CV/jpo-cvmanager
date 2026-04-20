import React, { useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Checkbox,
  Typography,
  Button,
  Box,
  Alert,
  styled,
} from '@mui/material'
import { EmailSubscription } from '../../models/email-subscriptions'
import { isSubscribed } from '../../models/email-subscriptions'

const StyledTableContainer = styled(TableContainer)`
  margin-top: 2rem;
  margin-bottom: 2rem;
`

const StyledTableCell = styled(TableCell)`
  font-weight: bold;
  background-color: #f5f5f5;
`

const CategoryCell = styled(TableCell)`
  font-weight: 500;
`

const SaveButtonContainer = styled(Box)`
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
  gap: 1rem;
`

interface EmailSubscriptionsTableProps {
  subscriptions: EmailSubscription[]
  onSave: (updatedSubscriptions: EmailSubscription[]) => Promise<void>
  showUnsubscribeAll?: boolean
  onUnsubscribeAll?: () => void
}

const EmailSubscriptionsTable: React.FC<EmailSubscriptionsTableProps> = ({
  subscriptions,
  onSave,
  showUnsubscribeAll = false,
  onUnsubscribeAll,
}) => {
  const [localSubscriptions, setLocalSubscriptions] = useState<EmailSubscription[]>(subscriptions)
  const [hasChanges, setHasChanges] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const handleCheckboxChange = (index: number, field: keyof EmailSubscription) => {
    const updated = [...localSubscriptions]
    updated[index] = {
      ...updated[index],
      [field]: !updated[index][field],
    }
    setLocalSubscriptions(updated)
    setHasChanges(true)
    setSaveSuccess(false)
    setSaveError(null)
  }

  const handleSave = async () => {
    setIsSaving(true)
    setSaveError(null)
    setSaveSuccess(false)

    try {
      await onSave(localSubscriptions)
      setHasChanges(false)
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 3000)
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : 'Failed to save changes')
    } finally {
      setIsSaving(false)
    }
  }

  const handleReset = () => {
    setLocalSubscriptions(subscriptions)
    setHasChanges(false)
    setSaveError(null)
    setSaveSuccess(false)
  }

  return (
    <>
      {saveError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {saveError}
        </Alert>
      )}
      {saveSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Changes saved successfully!
        </Alert>
      )}

      <StyledTableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <StyledTableCell>Category</StyledTableCell>
              <StyledTableCell>Description</StyledTableCell>
              <StyledTableCell align="center">Immediate</StyledTableCell>
              <StyledTableCell align="center">Hourly</StyledTableCell>
              <StyledTableCell align="center">Daily</StyledTableCell>
              <StyledTableCell align="center">Weekly</StyledTableCell>
              <StyledTableCell align="center">Monthly</StyledTableCell>
              <StyledTableCell align="center">Subscribed</StyledTableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {localSubscriptions.map((subscription, index) => (
              <TableRow key={subscription.category} hover>
                <CategoryCell>{subscription.category}</CategoryCell>
                <TableCell>
                  <Typography variant="body2">{subscription.description}</Typography>
                </TableCell>
                <TableCell align="center">
                  {subscription.supports_immediate ? (
                    <Checkbox
                      checked={subscription.immediate}
                      onChange={() => handleCheckboxChange(index, 'immediate')}
                      color="primary"
                    />
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="center">
                  {subscription.supports_hourly ? (
                    <Checkbox
                      checked={subscription.hourly}
                      onChange={() => handleCheckboxChange(index, 'hourly')}
                      color="primary"
                    />
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="center">
                  {subscription.supports_daily ? (
                    <Checkbox
                      checked={subscription.daily}
                      onChange={() => handleCheckboxChange(index, 'daily')}
                      color="primary"
                    />
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="center">
                  {subscription.supports_weekly ? (
                    <Checkbox
                      checked={subscription.weekly}
                      onChange={() => handleCheckboxChange(index, 'weekly')}
                      color="primary"
                    />
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="center">
                  {subscription.supports_monthly ? (
                    <Checkbox
                      checked={subscription.monthly}
                      onChange={() => handleCheckboxChange(index, 'monthly')}
                      color="primary"
                    />
                  ) : (
                    <Typography variant="body2" color="textSecondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="center">
                  <Typography variant="body2" color={isSubscribed(subscription) ? 'success.main' : 'error.main'}>
                    {isSubscribed(subscription) ? 'Yes' : 'No'}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </StyledTableContainer>

      <SaveButtonContainer>
        {showUnsubscribeAll && onUnsubscribeAll && (
          <Button variant="outlined" color="error" onClick={onUnsubscribeAll}>
            Unsubscribe from All
          </Button>
        )}
        {hasChanges && (
          <>
            <Button variant="outlined" onClick={handleReset} disabled={isSaving}>
              Reset
            </Button>
            <Button variant="contained" color="primary" onClick={handleSave} disabled={isSaving}>
              {isSaving ? 'Saving...' : 'Save Changes'}
            </Button>
          </>
        )}
      </SaveButtonContainer>
    </>
  )
}

export default EmailSubscriptionsTable
