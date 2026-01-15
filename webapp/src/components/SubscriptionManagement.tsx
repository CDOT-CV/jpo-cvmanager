import React, { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Paper,
  Typography,
  FormControlLabel,
  Checkbox,
  Button,
  Alert,
  CircularProgress,
  Container,
  Divider,
  FormGroup,
  useTheme,
} from '@mui/material'
import {
  useGetEmailSubscriptionsQuery,
  useUpdateEmailSubscriptionsMutation,
} from '../features/api/subscriptionManagementApiSlice'
import { EmailSubscription } from '../models/email-subscriptions'
import { headerTabHeight } from '../styles/index'
import { SecureStorageManager } from '../managers'

const SubscriptionManagement = () => {
  const theme = useTheme()
  const [subscriptions, setSubscriptions] = useState<Record<string, EmailSubscription>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  // Fetch email subscriptions with RTK Query
  const { data, isLoading, isFetching } = useGetEmailSubscriptionsQuery()
  const [updateEmailSubscriptions] = useUpdateEmailSubscriptionsMutation()

  const isOperatorOrAbove = useMemo(() => {
    const allowedRoles = ['operator', 'admin']
    return allowedRoles.includes(SecureStorageManager.getUserRole())
  }, [])

  const isAdmin = useMemo(() => {
    return SecureStorageManager.getUserRole() === 'admin'
  }, [])

  // Initialize subscriptions from API data
  useEffect(() => {
    if (data?.subscriptions) {
      const initialSubscriptions: Record<string, EmailSubscription> = {}
      data.subscriptions.forEach((cat) => {
        initialSubscriptions[cat.category] = { ...cat }
      })
      setSubscriptions(initialSubscriptions)
    }
  }, [data])

  const handleToggle = (categoryId: string) => {
    setSubscriptions((prev) => {
      const subscription = prev[categoryId]
      const newSubscribed = !isSubscribed(subscription)

      // If has frequencies, just toggle subscribed
      return {
        ...prev,
        [categoryId]: {
          ...subscription,
          immediate: subscription.supports_immediate && newSubscribed,
          hourly: subscription.supports_hourly && newSubscribed,
          daily: subscription.supports_daily && newSubscribed,
          weekly: subscription.supports_weekly && newSubscribed,
          monthly: subscription.supports_monthly && newSubscribed,
        },
      }
    })
  }

  const handleFrequencyToggle = (
    categoryId: string,
    frequency: 'immediate' | 'hourly' | 'daily' | 'weekly' | 'monthly'
  ) => {
    setSubscriptions((prev) => ({
      ...prev,
      [categoryId]: { ...prev[categoryId], [frequency]: !prev[categoryId][frequency] },
    }))
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    setSuccess(false)

    try {
      await updateEmailSubscriptions(Object.values(subscriptions)).unwrap()

      setSuccess(true)

      // Clear success message after 3 seconds
      setTimeout(() => {
        setSuccess(false)
      }, 3000)
    } catch (err) {
      setError('Failed to save subscription preferences. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const handleUnsubscribeAll = () => {
    setSubscriptions((prev) => {
      const updated: Record<string, EmailSubscription> = {}
      Object.keys(prev).forEach((category) => {
        updated[category] = {
          ...prev[category],
          immediate: false,
          hourly: false,
          daily: false,
          weekly: false,
          monthly: false,
        }
      })
      return updated
    })
  }

  const isSubscribed = (subscription: EmailSubscription) => {
    return (
      subscription?.immediate ||
      subscription?.hourly ||
      subscription?.daily ||
      subscription?.weekly ||
      subscription?.monthly
    )
  }

  const availableCategories = useMemo(() => {
    const categories = data?.subscriptions || []
    return categories.filter((cat) => {
      if (cat.requiredRole === 'admin') {
        return isAdmin
      }
      if (cat.requiredRole === 'operator') {
        return isOperatorOrAbove
      }
      return true // 'user' role is available to everyone
    })
  }, [data?.subscriptions, isAdmin, isOperatorOrAbove])

  // Show loading while fetching data OR while subscriptions state is being initialized
  if (isLoading || isFetching || Object.keys(subscriptions).length === 0) {
    return (
      <Container maxWidth="md">
        <Box display="flex" justifyContent="center" alignItems="center" minHeight="100vh">
          <CircularProgress />
        </Box>
      </Container>
    )
  }

  return (
    <Container
      maxWidth={false}
      sx={{ backgroundColor: theme.palette.background.default, height: `calc(100vh - ${headerTabHeight}px)` }}
    >
      <Container maxWidth="md">
        <Box sx={{ py: 4 }}>
          <Paper elevation={3} sx={{ p: 4 }}>
            <Typography variant="h4" component="h1" gutterBottom>
              Email Subscription Preferences
            </Typography>

            {error && (
              <Alert severity="error" sx={{ mb: 3 }}>
                {error}
              </Alert>
            )}

            {success && (
              <Alert severity="success" sx={{ mb: 3 }}>
                Subscription preferences saved successfully!
              </Alert>
            )}

            <Divider sx={{ my: 3 }} />

            <FormGroup>
              {availableCategories.map((cat) => {
                return (
                  <Box
                    key={cat.category}
                    sx={{
                      p: 2,
                      mb: 2,
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 1,
                      backgroundColor: 'transparent',
                    }}
                  >
                    <Box>
                      <Typography variant="body1" fontWeight="medium">
                        {cat.category}
                        {cat.requiredRole != 'user' && (
                          <Typography
                            component="span"
                            variant="caption"
                            sx={{
                              ml: 1,
                              px: 1,
                              py: 0.5,
                              backgroundColor: 'primary.main',
                              color: 'primary.contrastText',
                              borderRadius: 1,
                            }}
                          >
                            {cat.requiredRole}
                          </Typography>
                        )}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {cat.description}
                      </Typography>
                    </Box>

                    {/* Frequency Options */}

                    <Box sx={{ ml: 4, mt: 2, display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                      {cat.supports_immediate && (
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={subscriptions[cat.category]?.immediate || false}
                              onChange={() => handleFrequencyToggle(cat.category, 'immediate')}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">Immediate</Typography>}
                        />
                      )}

                      {cat.supports_hourly && (
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={subscriptions[cat.category]?.hourly || false}
                              onChange={() => handleFrequencyToggle(cat.category, 'hourly')}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">Hourly</Typography>}
                        />
                      )}

                      {cat.supports_daily && (
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={subscriptions[cat.category]?.daily || false}
                              onChange={() => handleFrequencyToggle(cat.category, 'daily')}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">Daily</Typography>}
                        />
                      )}

                      {cat.supports_weekly && (
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={subscriptions[cat.category]?.weekly || false}
                              onChange={() => handleFrequencyToggle(cat.category, 'weekly')}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">Weekly</Typography>}
                        />
                      )}

                      {cat.supports_monthly && (
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={subscriptions[cat.category]?.monthly || false}
                              onChange={() => handleFrequencyToggle(cat.category, 'monthly')}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">Monthly</Typography>}
                        />
                      )}
                    </Box>
                  </Box>
                )
              })}
            </FormGroup>

            {availableCategories.length === 0 && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                Unable to retrieve subscription details - this unsubscribe link may be invalid
              </Alert>
            )}

            <Divider sx={{ my: 3 }} />

            <Box sx={{ display: 'flex', gap: 2, justifyContent: 'space-between' }}>
              <Button
                variant="outlined"
                color="warning"
                onClick={handleUnsubscribeAll}
                disabled={saving || availableCategories.length === 0}
              >
                Unsubscribe from All
              </Button>
              <Button
                variant="contained"
                color="primary"
                onClick={handleSave}
                disabled={saving || availableCategories.length === 0}
                startIcon={saving && <CircularProgress size={20} />}
              >
                {saving ? 'Saving...' : 'Save Preferences'}
              </Button>
            </Box>

            <Box sx={{ mt: 3 }}>
              <Typography variant="caption" color="text.secondary">
                Your subscription preferences will be applied immediately. You can update these settings at any time.
              </Typography>
            </Box>
          </Paper>
        </Box>
      </Container>
    </Container>
  )
}

export default SubscriptionManagement
