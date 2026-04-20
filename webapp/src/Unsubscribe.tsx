import { useSearchParams } from 'react-router-dom'
import { Box, CircularProgress, Container } from '@mui/material'
import { useGetEmailSubscriptionsQuery, useUpdateEmailSubscriptionsMutation } from './features/api/unsubscribeApiSlice'
import SubscriptionForm from './components/SubscriptionForm'
import { EmailSubscription } from './models/email-subscriptions'

const Unsubscribe = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  // Filter categories based on admin status
  const { data, isLoading, isFetching } = useGetEmailSubscriptionsQuery(token)
  const [updateEmailSubscriptions] = useUpdateEmailSubscriptionsMutation()

  const handleSave = async (subscriptions: EmailSubscription[]) =>
    updateEmailSubscriptions({ token, subscriptions }).unwrap()

  if (isLoading || isFetching) {
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
        <SubscriptionForm
          subscriptions={Object.values(data?.subscriptions || {})}
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
