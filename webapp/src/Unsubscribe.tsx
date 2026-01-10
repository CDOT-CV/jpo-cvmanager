import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
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
  Switch,
  FormGroup,
  useTheme,
} from '@mui/material'
import { SecureStorageManager } from './managers'

// Define subscription categories
interface SubscriptionCategory {
  id: string
  name: string
  description: string
  adminOnly: boolean
}

const SUBSCRIPTION_CATEGORIES: SubscriptionCategory[] = [
  {
    id: 'contact-support',
    name: 'Contact Support Notifications',
    description: 'Receive notifications about contact support requests and updates',
    adminOnly: true,
  },
  {
    id: 'intersection-events',
    name: 'Intersection Events',
    description: 'Notifications about intersection status changes and events',
    adminOnly: false,
  },
  {
    id: 'system-alerts',
    name: 'System Alerts',
    description: 'Important system-wide alerts and maintenance notifications',
    adminOnly: false,
  },
  {
    id: 'weekly-reports',
    name: 'Weekly Reports',
    description: 'Weekly summary reports of system activity',
    adminOnly: false,
  },
  {
    id: 'admin-notifications',
    name: 'Admin Notifications',
    description: 'Administrative notifications and user management alerts',
    adminOnly: true,
  },
]

const Unsubscribe = () => {
  const theme = useTheme()
  const { category } = useParams<{ category: string }>()
  const navigate = useNavigate()
  const [subscriptions, setSubscriptions] = useState<Record<string, boolean>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const isAdmin = SecureStorageManager.getUserRole() === 'admin'

  // Filter categories based on admin status
  const availableCategories = SUBSCRIPTION_CATEGORIES.filter((cat) => !cat.adminOnly || isAdmin)

  // Initialize subscriptions
  useEffect(() => {
    const loadSubscriptions = async () => {
      setLoading(true)
      setError(null)

      try {
        // TODO: Replace with actual API call to fetch user's current subscriptions
        // For now, initialize all as subscribed
        const initialSubscriptions: Record<string, boolean> = {}
        availableCategories.forEach((cat) => {
          initialSubscriptions[cat.id] = true
        })
        setSubscriptions(initialSubscriptions)
      } catch (err) {
        setError('Failed to load subscription preferences')
      } finally {
        setLoading(false)
      }
    }

    loadSubscriptions()
  }, [isAdmin])

  // Check if the category from URL is valid
  useEffect(() => {
    if (category && !availableCategories.find((cat) => cat.id === category)) {
      setError(`Invalid subscription category: ${category}`)
    }
  }, [category, availableCategories])

  const handleToggle = (categoryId: string) => {
    setSubscriptions((prev) => ({
      ...prev,
      [categoryId]: !prev[categoryId],
    }))
  }

  const handleToggleAll = (subscribe: boolean) => {
    const updatedSubscriptions: Record<string, boolean> = {}
    availableCategories.forEach((cat) => {
      updatedSubscriptions[cat.id] = subscribe
    })
    setSubscriptions(updatedSubscriptions)
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    setSuccess(false)

    try {
      // TODO: Replace with actual API call to save subscription preferences
      // Example: await saveSubscriptionPreferences(subscriptions)

      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000))

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

  const allSubscribed = availableCategories.every((cat) => subscriptions[cat.id])
  const noneSubscribed = availableCategories.every((cat) => !subscriptions[cat.id])

  if (loading) {
    return (
      <Container maxWidth="md">
        <Box display="flex" justifyContent="center" alignItems="center" minHeight="100vh">
          <CircularProgress />
        </Box>
      </Container>
    )
  }

  return (
    <Container maxWidth="md">
      <Box sx={{ py: 4 }}>
        <Paper elevation={3} sx={{ p: 4 }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Email Subscription Preferences
          </Typography>

          {category && (
            <Alert severity="info" sx={{ mb: 3 }}>
              Manage your subscription for: <strong>{category}</strong>
            </Alert>
          )}

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

          {/* Toggle All Switch */}
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              mb: 3,
              p: 2,
              backgroundColor: theme.palette.mode === 'dark' ? 'grey.900' : 'grey.100',
              borderRadius: 1,
            }}
          >
            <Typography variant="body1" fontWeight="bold">
              {allSubscribed ? 'Unsubscribe from All' : 'Subscribe to All'}
            </Typography>
            <Switch checked={allSubscribed} onChange={(e) => handleToggleAll(e.target.checked)} color="primary" />
          </Box>

          {/* Subscription List */}
          <FormGroup>
            {availableCategories.map((cat) => (
              <Box
                key={cat.id}
                sx={{
                  p: 2,
                  mb: 2,
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  backgroundColor:
                    category === cat.id
                      ? theme.palette.mode === 'dark'
                        ? 'action.selected'
                        : 'action.hover'
                      : 'transparent',
                }}
              >
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={subscriptions[cat.id] || false}
                      onChange={() => handleToggle(cat.id)}
                      color="primary"
                    />
                  }
                  label={
                    <Box>
                      <Typography variant="body1" fontWeight="medium">
                        {cat.name}
                        {cat.adminOnly && (
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
                            Admin Only
                          </Typography>
                        )}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {cat.description}
                      </Typography>
                    </Box>
                  }
                  sx={{ m: 0, alignItems: 'flex-start' }}
                />
              </Box>
            ))}
          </FormGroup>

          {availableCategories.length === 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              No subscription categories available for your role.
            </Alert>
          )}

          <Divider sx={{ my: 3 }} />

          {/* Action Buttons */}
          <Box sx={{ display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
            <Button variant="outlined" onClick={() => navigate('/dashboard')} disabled={saving}>
              Cancel
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

          {/* Info Text */}
          <Box sx={{ mt: 3 }}>
            <Typography variant="caption" color="text.secondary">
              Your subscription preferences will be applied immediately. You can update these settings at any time.
            </Typography>
          </Box>
        </Paper>
      </Box>
    </Container>
  )
}

export default Unsubscribe
