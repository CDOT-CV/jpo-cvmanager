import { useEffect, useMemo, useState } from 'react'
import {
  Box,
  Paper,
  Typography,
  FormControlLabel,
  Checkbox,
  Button,
  Alert,
  CircularProgress,
  Divider,
  FormGroup,
  Chip,
  Switch,
} from '@mui/material'
import HomeIcon from '@mui/icons-material/Home'
import { EmailSubscription } from '../models/email-subscriptions'
import { useNavigate } from 'react-router-dom'

interface SubscriptionFormProps {
  subscriptions: EmailSubscription[]
  onSave: (subscriptions: EmailSubscription[]) => Promise<void>
  title?: string
  showUnsubscribeAll?: boolean
  showHomepageLink?: boolean
}

type Frequency = 'immediate' | 'hourly' | 'daily' | 'weekly' | 'monthly'

const FREQUENCIES: {
  key: Frequency
  supportsKey: keyof EmailSubscription
  label: string
}[] = [
  { key: 'immediate', supportsKey: 'supports_immediate', label: 'Immediate' },
  { key: 'hourly', supportsKey: 'supports_hourly', label: 'Hourly' },
  { key: 'daily', supportsKey: 'supports_daily', label: 'Daily' },
  { key: 'weekly', supportsKey: 'supports_weekly', label: 'Weekly' },
  { key: 'monthly', supportsKey: 'supports_monthly', label: 'Monthly' },
]

const isRequiredRoleUser = (role: string): boolean => {
  const normalizedRole = role.toLowerCase()
  return normalizedRole === 'user'
}

export const SYSTEM_WIDE_LABEL = 'System-wide'

// Heading for a group of subscriptions; system-wide email types have no organization
export const groupLabel = (organization: string | null): string => organization ?? SYSTEM_WIDE_LABEL

export const subscriptionKey = (sub: EmailSubscription): string => `${sub.organization ?? ''}|${sub.category}`

export const isSubscribed = (sub: EmailSubscription): boolean =>
  sub.immediate || sub.hourly || sub.daily || sub.weekly || sub.monthly

// First frequency the email type supports, e.g. Daily for Daily Message Counts
export const defaultFrequency = (sub: EmailSubscription): Frequency | undefined =>
  FREQUENCIES.find((freq) => sub[freq.supportsKey])?.key

export const setSubscribed = (sub: EmailSubscription, subscribed: boolean): EmailSubscription => {
  const cleared = {
    ...sub,
    immediate: false,
    hourly: false,
    daily: false,
    weekly: false,
    monthly: false,
  }
  if (!subscribed) return cleared
  if (isSubscribed(sub)) return sub
  const frequency = defaultFrequency(sub)
  return frequency ? { ...cleared, [frequency]: true } : cleared
}

// Groups subscriptions by organization, keeping the order the API returned them in.
// System-wide email types are grouped under a null organization.
export const groupByOrganization = (subs: EmailSubscription[]): [string | null, EmailSubscription[]][] => {
  const groups = new Map<string | null, EmailSubscription[]>()
  subs.forEach((sub) => {
    groups.set(sub.organization, [...(groups.get(sub.organization) ?? []), sub])
  })
  return Array.from(groups.entries())
}

const toRecord = (subs: EmailSubscription[]): Record<string, EmailSubscription> => {
  const record: Record<string, EmailSubscription> = {}
  subs.forEach((sub) => {
    record[subscriptionKey(sub)] = { ...sub }
  })
  return record
}

const SubscriptionForm = ({
  subscriptions: initialSubscriptions,
  onSave,
  title = 'Email Subscription Preferences',
  showUnsubscribeAll = true,
  showHomepageLink = true,
}: SubscriptionFormProps) => {
  const navigate = useNavigate()
  const [subscriptions, setSubscriptions] = useState<Record<string, EmailSubscription>>(() =>
    toRecord(initialSubscriptions)
  )
  const [prevSubscriptions, setPrevSubscriptions] = useState<Record<string, EmailSubscription>>(() =>
    toRecord(initialSubscriptions)
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const organizations = useMemo(() => groupByOrganization(initialSubscriptions), [initialSubscriptions])

  const isModified = useMemo(() => {
    return Object.entries(prevSubscriptions).some(([key, sub]) => {
      const current = subscriptions[key]
      return FREQUENCIES.some((freq) => current?.[freq.key] !== sub[freq.key])
    })
  }, [prevSubscriptions, subscriptions])

  useEffect(() => {
    // If the user has made changes, we don't want to overwrite their selections when initialSubscriptions updates
    if (isModified) return

    const initial = toRecord(initialSubscriptions)
    setSubscriptions(initial)
    setPrevSubscriptions(initial)
  }, [initialSubscriptions])

  const handleFrequencyToggle = (key: string, frequency: Frequency) => {
    setSubscriptions((prev) => ({
      ...prev,
      [key]: { ...prev[key], [frequency]: !prev[key][frequency] },
    }))
  }

  const handleSubscriptionToggle = (key: string, subscribed: boolean) => {
    setSubscriptions((prev) => ({
      ...prev,
      [key]: setSubscribed(prev[key], subscribed),
    }))
  }

  const handleOrganizationToggle = (organization: string | null, subscribed: boolean) => {
    setSubscriptions((prev) => {
      const updated = { ...prev }
      Object.entries(prev).forEach(([key, sub]) => {
        if (sub.organization === organization) {
          updated[key] = setSubscribed(sub, subscribed)
        }
      })
      return updated
    })
  }

  const handleSave = async (e?: React.FormEvent) => {
    // Prevent form submission if triggered by form submit event
    e?.preventDefault()

    setSaving(true)
    setError(null)
    setSuccess(false)

    try {
      await onSave(Object.values(subscriptions))
      setPrevSubscriptions({ ...subscriptions })
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
      Object.entries(prev).forEach(([key, sub]) => {
        updated[key] = setSubscribed(sub, false)
      })
      return updated
    })
  }

  return (
    <Paper elevation={3} sx={{ p: 2 }}>
      <Typography variant="h5" component="h3" gutterBottom>
        {title}
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {success && (
        <>
          <Alert severity="success" sx={{ mb: 2 }}>
            Subscription preferences saved successfully!
          </Alert>
        </>
      )}

      <Divider sx={{ my: 1.5 }} />

      {organizations.map(([organization, orgSubscriptions]) => {
        const label = groupLabel(organization)
        const subscribedCount = orgSubscriptions.filter((sub) => {
          const current = subscriptions[subscriptionKey(sub)]
          return current && isSubscribed(current)
        }).length

        return (
          <Box
            key={label}
            component="section"
            aria-label={`${label} email subscriptions`}
            sx={{ mb: 2, border: 1, borderColor: 'divider', borderRadius: 1 }}
          >
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 1,
                px: 1.5,
                py: 1,
                backgroundColor: 'action.hover',
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="h6" component="h4">
                  {label}
                </Typography>
                <Chip
                  size="small"
                  label={
                    subscribedCount > 0
                      ? `Subscribed to ${subscribedCount} of ${orgSubscriptions.length}`
                      : 'Not subscribed'
                  }
                />
              </Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={subscribedCount > 0}
                    onChange={(e) => handleOrganizationToggle(organization, e.target.checked)}
                    color="secondary"
                    inputProps={{
                      'aria-label': `Subscribe to all ${label} emails`,
                    }}
                  />
                }
                label={<Typography variant="body2">{subscribedCount > 0 ? 'Subscribed' : 'Unsubscribed'}</Typography>}
                labelPlacement="start"
                sx={{ m: 0 }}
              />
            </Box>

            <FormGroup sx={{ p: 0.75 }}>
              {orgSubscriptions.map((cat) => {
                const key = subscriptionKey(cat)
                const current = subscriptions[key] ?? cat
                const subscribed = isSubscribed(current)

                return (
                  <Box
                    key={key}
                    sx={{
                      p: 0.75,
                      mb: 0.75,
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 0.5,
                      backgroundColor: 'transparent',
                    }}
                  >
                    <Box
                      sx={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: 1,
                      }}
                    >
                      <Box>
                        <Typography variant="body1" fontWeight="medium">
                          {cat.category}
                          {!isRequiredRoleUser(cat.required_role) && (
                            <Typography
                              component="span"
                              variant="caption"
                              sx={{
                                ml: 1,
                                px: 1,
                                py: 0.25,
                                backgroundColor: 'primary.main',
                                color: 'primary.contrastText',
                                borderRadius: 1,
                              }}
                            >
                              {cat.required_role}
                            </Typography>
                          )}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {cat.description}
                        </Typography>
                      </Box>
                      <Switch
                        checked={subscribed}
                        onChange={(e) => handleSubscriptionToggle(key, e.target.checked)}
                        color="secondary"
                        size="small"
                        inputProps={{
                          'aria-label': organization
                            ? `Subscribe to ${cat.category} for ${organization}`
                            : `Subscribe to ${cat.category}`,
                        }}
                      />
                    </Box>

                    {/* Frequency Options */}
                    <Box
                      sx={{
                        ml: 1.5,
                        mt: 0,
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 0,
                      }}
                    >
                      {FREQUENCIES.filter((freq) => cat[freq.supportsKey]).map((freq) => (
                        <FormControlLabel
                          key={freq.key}
                          control={
                            <Checkbox
                              checked={current[freq.key] || false}
                              onChange={() => handleFrequencyToggle(key, freq.key)}
                              disabled={!subscribed}
                              color="secondary"
                              size="small"
                            />
                          }
                          label={<Typography variant="body2">{freq.label}</Typography>}
                          sx={{ my: 0, mr: 1, minHeight: 24 }}
                        />
                      ))}
                    </Box>
                  </Box>
                )
              })}
            </FormGroup>
          </Box>
        )
      })}

      {initialSubscriptions.length === 0 && (
        <Alert severity="warning" sx={{ mt: 2 }}>
          Unable to retrieve subscription details - this unsubscribe link may be invalid
        </Alert>
      )}

      <Divider sx={{ my: 1.5 }} />

      <Box sx={{ display: 'flex', gap: 1, justifyContent: 'space-between' }}>
        <Box>
          {showHomepageLink && (
            <Button
              variant="outlined"
              startIcon={<HomeIcon />}
              onClick={() => navigate('/')}
              disabled={saving}
              size="small"
              sx={{ mr: 0.5, minHeight: 32, px: 1 }}
            >
              Home
            </Button>
          )}
          {showUnsubscribeAll && (
            <Button
              variant="outlined"
              color="warning"
              onClick={handleUnsubscribeAll}
              disabled={saving || initialSubscriptions.length === 0}
              size="small"
              sx={{ minHeight: 32, px: 1 }}
            >
              Unsubscribe from All
            </Button>
          )}
        </Box>
        <Button
          variant="contained"
          color="primary"
          onClick={handleSave}
          disabled={saving || initialSubscriptions.length === 0 || !isModified}
          startIcon={saving && <CircularProgress size={20} />}
          size="small"
          sx={{ ml: showUnsubscribeAll ? 0 : 'auto', minHeight: 32, px: 1.25 }}
        >
          {saving ? 'Saving...' : 'Save Preferences'}
        </Button>
      </Box>

      <Box sx={{ mt: 1.5 }}>
        <Typography variant="caption" color="text.secondary">
          Organization emails are set separately for each organization, and system-wide emails apply across all of them.
          Your preferences will be applied immediately, and you can update these settings at any time.
        </Typography>
      </Box>
    </Paper>
  )
}

export default SubscriptionForm
