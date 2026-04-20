import { useMemo } from 'react'
import { Box, CircularProgress, Container, useTheme } from '@mui/material'
import {
  useGetEmailSubscriptionsQuery,
  useUpdateEmailSubscriptionsMutation,
} from '../features/api/subscriptionManagementApiSlice'
import { EmailSubscription } from '../models/email-subscriptions'
import { headerTabHeight } from '../styles/index'
import { selectRole } from '../generalSlices/userSlice'
import { useSelector } from 'react-redux'
import SubscriptionForm from './SubscriptionForm'

const isRoleOperator = (role: string): boolean => {
  const normalizedRole = role.toLowerCase()
  return normalizedRole === 'operator'
}

const isRoleAdmin = (role: string): boolean => {
  const normalizedRole = role.toLowerCase()
  return normalizedRole === 'admin'
}

const isRoleOperatorOrAbove = (role: string): boolean => {
  const normalizedRole = role.toLowerCase()
  return normalizedRole === 'operator' || normalizedRole === 'admin'
}

const SubscriptionManagement = () => {
  const theme = useTheme()

  // Fetch email subscriptions with RTK Query
  const { data, isLoading, isFetching } = useGetEmailSubscriptionsQuery()
  const [updateEmailSubscriptions] = useUpdateEmailSubscriptionsMutation()

  const userRole = useSelector(selectRole)

  const handleSave = async (subscriptions: EmailSubscription[]) => updateEmailSubscriptions(subscriptions).unwrap()

  const availableCategories = useMemo(() => {
    const categories = data?.subscriptions || []
    return categories.filter((cat) => {
      if (isRoleAdmin(cat.required_role)) {
        return isRoleAdmin(userRole)
      }
      if (isRoleOperator(cat.required_role)) {
        return isRoleOperatorOrAbove(userRole)
      }
      return true // 'user' role is available to everyone
    })
  }, [data?.subscriptions, userRole])

  // Show loading while fetching data OR while subscriptions state is being initialized
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
    <Container
      maxWidth={false}
      sx={{ backgroundColor: theme.palette.background.default, height: `calc(100vh - ${headerTabHeight}px)` }}
    >
      <Container maxWidth="md">
        <Box sx={{ py: 4 }}>
          <SubscriptionForm
            subscriptions={availableCategories}
            onSave={handleSave}
            title="Manage Your Email Subscriptions"
            showUnsubscribeAll={true}
            showHomepageLink={false}
          />
        </Box>
      </Container>
    </Container>
  )
}

export default SubscriptionManagement
