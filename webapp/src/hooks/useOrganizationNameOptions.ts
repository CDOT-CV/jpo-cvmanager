import { useMemo } from 'react'
import { useSelector } from 'react-redux'
import { useGetOrganizationsQuery } from '../features/api/organizationApiSlice'
import { getFilteredOrganizationNameObjects, selectAuthLoginData, selectSuperUser } from '../generalSlices/userSlice'

export const useOrganizationNameOptions = (organizationIds: number[]) => {
  const authLoginData = useSelector(selectAuthLoginData)
  const isSuperUser = useSelector(selectSuperUser)
  const { data: organizationDirectory = [] } = useGetOrganizationsQuery(undefined, { skip: !isSuperUser })

  return useMemo(
    () =>
      getFilteredOrganizationNameObjects(
        organizationIds,
        authLoginData?.data.organizations ?? [],
        isSuperUser ? organizationDirectory : []
      ),
    [authLoginData, isSuperUser, organizationDirectory, organizationIds]
  )
}
