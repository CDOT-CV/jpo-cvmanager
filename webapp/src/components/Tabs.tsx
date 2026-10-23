import PropTypes from 'prop-types'
import { Component } from 'react'
import { evaluateFeatureFlags } from '../feature-flags'
import { TabListContainer } from '../styles/components/HorizontalTabs'
import Tab from './Tab'

interface TabItemProps {
  label: string
  path: string
  tag?: FEATURE_KEY
}

export const TabItem = (_: TabItemProps) => {
  return <div></div>
}

interface TabsProps {
  children: {
    props: TabItemProps
  }[]
}

interface TabsState {
  activeTab: string
}

class Tabs extends Component<TabsProps, TabsState> {
  static propTypes = {
    children: PropTypes.instanceOf(Array).isRequired,
  }

  constructor(props: TabsProps) {
    super(props)

    this.state = {
      activeTab: props.children[0].props.label,
    }
  }

  onClickTabItem = (tab: string) => {
    this.setState({ activeTab: tab })
  }

  render() {
    const {
      onClickTabItem,
      props: { children },
      state: { activeTab },
    } = this

    return (
      <div className="tabs">
        <TabListContainer>
          {children
            .filter((child) => evaluateFeatureFlags(child.props.tag))
            .map((child) => {
              const label = child?.props?.label
              const path = child?.props?.path
              if (label !== undefined) {
                return <Tab path={path} activeTab={activeTab} key={label} label={label} onClick={onClickTabItem} />
              } else {
                return null
              }
            })}
        </TabListContainer>
        <div className="tab-content"></div>
      </div>
    )
  }
}

export default Tabs
