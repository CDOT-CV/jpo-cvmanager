import { render } from '@testing-library/react'
import RsuMarker from './RsuMarker'
import { replaceChaoticIds } from '../utils/test-utils'

it('snapshot online online', () => {
  const { container } = render(<RsuMarker displayType="online" onlineStatus="online" />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot online unstable', () => {
  const { container } = render(<RsuMarker displayType="online" onlineStatus="unstable" />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot online offline', () => {
  const { container } = render(<RsuMarker displayType="online" onlineStatus="offline" />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot online other', () => {
  const { container } = render(<RsuMarker displayType="online" onlineStatus="other" />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot scms true', () => {
  const { container } = render(<RsuMarker displayType="scms" scmsStatus={true} />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot scms false', () => {
  const { container } = render(<RsuMarker displayType="online" scmsStatus={false} />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot scms undefined', () => {
  const { container } = render(<RsuMarker displayType="online" scmsStatus={undefined} />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot other', () => {
  const { container } = render(<RsuMarker displayType="other" />)
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})
