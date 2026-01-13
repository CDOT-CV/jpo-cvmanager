export type EmailSubscriptionUpdateRequest = {
  subscriptions: EmailSubscription[]
  email: string
}

export type EmailSubscriptionGetResponse = {
  subscriptions: EmailSubscription[]
  email: string
}

export type EmailSubscription = {
  category: string
  description: string
  requiredRole: string
  subscribed: boolean
}
