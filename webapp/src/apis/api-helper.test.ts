import { apiHelper } from './api-helper'
import fetchMock from 'jest-fetch-mock'

beforeEach(() => {
  fetchMock.mockClear()
  fetchMock.doMock()
})

it('Test format query params', async () => {
  let queryParams = {}
  let response = apiHelper.formatQueryParams(queryParams)
  expect(response).toEqual('')

  queryParams = { email: 'jacob', password: 'password' }
  response = apiHelper.formatQueryParams(queryParams)
  expect(response).toEqual('?email=jacob&password=password')

  queryParams = { email: '', password: '' }
  response = apiHelper.formatQueryParams(queryParams)
  expect(response).toEqual('')
})

it('Test fetch request', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await apiHelper.invokeApi({ path: 'https://test.com', token: 'testToken' })
  expect(actualResponse).toEqual(expectedResponse)
})

it('Test fetch request Error', async () => {
  fetchMock.mockRejectOnce(new Error('fake error message'))
  const actualResponse = await apiHelper.invokeApi({ path: 'https://test.com', token: 'testToken' })
  expect(actualResponse).toEqual(null)
})

it('Test post request', async () => {
  let expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  let actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'POST',
  })
  expect(actualResponse.body).toEqual(expectedResponse)

  fetchMock.mockResponseOnce('NOT JSON')
  actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'POST',
  })
  expect(actualResponse.body).toEqual(undefined)
})

it('Test post request Error', async () => {
  fetchMock.mockRejectOnce(new Error('fake error message'))
  const actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'POST',
  })
  expect(actualResponse).toEqual(null)
})

it('Test delete request', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  let actualResponse = await apiHelper.invokeApi({ path: 'https://test.com', token: 'testToken', method: 'DELETE' })
  expect(actualResponse.body).toEqual(expectedResponse)

  fetchMock.mockResponseOnce('NOT JSON')
  actualResponse = await apiHelper.invokeApi({ path: 'https://test.com', token: 'testToken', method: 'DELETE' })
  expect(actualResponse.body).toEqual(undefined)
})

it('Test delete request Error', async () => {
  fetchMock.mockRejectOnce(new Error('fake error message'))
  const actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    method: 'DELETE',
  })
  expect(actualResponse).toEqual(null)
})

it('Test patch request', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  let actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'PATCH',
  })
  expect(actualResponse.body).toEqual(expectedResponse)

  fetchMock.mockResponseOnce('NOT JSON')
  actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'PATCH',
  })
  expect(actualResponse.body).toEqual(undefined)
})

it('Test patch request Error', async () => {
  fetchMock.mockRejectOnce(new Error('fake error message'))
  const actualResponse = await apiHelper.invokeApi({
    path: 'https://test.com',
    token: 'testToken',
    body: {},
    method: 'PATCH',
  })
  expect(actualResponse).toEqual(null)
})
