export type EmailUnsubscribeUpdateRequest = {
  subscriptions: EmailSubscription[]
  email: string
}

export type EmailUnsubscribeGetResponse = {
  subscriptions: EmailSubscription[]
  email: string
}

export type EmailSubscription = {
  category: string
  description: string
  requiredRole: string
  immediate: boolean
  hourly: boolean
  daily: boolean
  weekly: boolean
  monthly: boolean
  supports_immediate: boolean
  supports_hourly: boolean
  supports_daily: boolean
  supports_weekly: boolean
  supports_monthly: boolean
}
