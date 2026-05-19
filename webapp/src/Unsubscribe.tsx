import { useSearchParams } from 'react-router-dom'
import { Box, CircularProgress, Container, LinearProgress } from '@mui/material'
import { useGetEmailSubscriptionsQuery, useUpdateEmailSubscriptionsMutation } from './features/api/unsubscribeApiSlice'
import SubscriptionForm from './components/SubscriptionForm'
import { EmailSubscription } from './models/email-subscriptions'

const Unsubscribe = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const { data, isLoading, isFetching } = useGetEmailSubscriptionsQuery(token ?? '', { skip: !token })
  const [updateEmailSubscriptions] = useUpdateEmailSubscriptionsMutation()

  if (!token) {
    return (
      <Container maxWidth="md">
        <Box display="flex" justifyContent="center" alignItems="center" minHeight="100vh">
          <p>Invalid or missing token. Please check your email link.</p>
        </Box>
      </Container>
    )
  }

  const handleSave = async (subscriptions: EmailSubscription[]) => {
    await updateEmailSubscriptions({ token, subscriptions }).unwrap()
  }

  if (isLoading) {
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
      <Box sx={{ py: 4, position: 'relative' }}>
        {/* Show progress bar during refetch, but keep form mounted */}
        {isFetching && (
          <LinearProgress
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              zIndex: 1000,
            }}
          />
        )}
        <SubscriptionForm
          subscriptions={data?.subscriptions ?? []}
          onSave={handleSave}
          title="Manage Your Email Subscriptions"
          showUnsubscribeAll={true}
          showHomepageLink={true}
        />
      </Box>
    </Container>
  )
}

export default Unsubscribe
