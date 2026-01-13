import React, { useState, useEffect } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
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
import HomeIcon from '@mui/icons-material/Home'
import { SecureStorageManager } from './managers'
import {
  useGetEmailSubscriptionsQuery,
  useUpdateEmailSubscriptionsMutation,
} from './features/api/userNotificationSlice'
import { EmailSubscription } from './models/email-subscriptions'

const Unsubscribe = () => {
  const theme = useTheme()
  const { category } = useParams<{ category: string }>()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const navigate = useNavigate()
  const [subscriptions, setSubscriptions] = useState<Record<string, EmailSubscription>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const isAdmin = SecureStorageManager.getUserRole() === 'admin'

  // Filter categories based on admin status
  const { data } = useGetEmailSubscriptionsQuery(token)
  const [updateEmailSubscriptions] = useUpdateEmailSubscriptionsMutation()
  const availableCategories = data?.subscriptions || []
  const userEmail = data?.email || ''

  // Initialize subscriptions
  useEffect(() => {
    const loadSubscriptions = async () => {
      setLoading(true)
      setError(null)

      // Validate token if present
      if (!token) {
        setError('Invalid or missing authentication token')
        setLoading(false)
        return
      }

      try {
        // TODO: Replace with actual API call to fetch user's current subscriptions
        // Pass the token to authenticate the request
        // Example: await fetchSubscriptionPreferences(token)

        // For now, initialize all as subscribed
        const initialSubscriptions: Record<string, EmailSubscription> = {}
        availableCategories.forEach((cat) => {
          initialSubscriptions[cat.category] = cat
        })
        setSubscriptions(initialSubscriptions)
      } catch (err) {
        setError('Failed to load subscription preferences')
      } finally {
        setLoading(false)
      }
    }

    loadSubscriptions()
  }, [token, isAdmin, availableCategories])

  // Check if the category from URL is valid
  useEffect(() => {
    if (category && !availableCategories.find((cat) => cat.category === category)) {
      setError(`Invalid subscription category: ${category}`)
    }
  }, [category, availableCategories])

  const handleToggle = (categoryId: string) => {
    setSubscriptions((prev) => ({
      ...prev,
      [categoryId]: { ...prev[categoryId], subscribed: !prev[categoryId].subscribed },
    }))
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    setSuccess(false)

    if (!token) {
      setError('Invalid or missing authentication token')
      setSaving(false)
      return
    }

    try {
      await updateEmailSubscriptions({ token, subscriptions: Object.values(subscriptions) }).unwrap()

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

          <FormGroup>
            {availableCategories.map((cat) => (
              <Box
                key={cat.category}
                sx={{
                  p: 2,
                  mb: 2,
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  backgroundColor:
                    category === cat.category
                      ? theme.palette.mode === 'dark'
                        ? 'action.selected'
                        : 'action.hover'
                      : 'transparent',
                }}
              >
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={subscriptions[cat.category]?.subscribed || false}
                      onChange={() => handleToggle(cat.category)}
                      color="primary"
                    />
                  }
                  label={
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

          <Box sx={{ display: 'flex', gap: 2, justifyContent: 'space-between' }}>
            <Button variant="outlined" startIcon={<HomeIcon />} onClick={() => navigate('/')} disabled={saving}>
              Home
            </Button>
            <Box sx={{ display: 'flex', gap: 2 }}>
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
          </Box>

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
