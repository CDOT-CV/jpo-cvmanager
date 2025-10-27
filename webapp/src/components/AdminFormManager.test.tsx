import React from 'react'
import { render } from '@testing-library/react'
import AdminFormManager from './AdminFormManager'
import { replaceChaoticIds } from '../utils/test-utils'
import { setupStore } from '../store'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { BrowserRouter } from 'react-router-dom'
import * as adminRsuTabSlice from '../features/adminRsuTab/adminRsuTabSlice'

describe('AdminFormManager snapshots', () => {
    afterEach(() => {
        jest.restoreAllMocks(); // restores original implementations after each test
    });

    it('snapshot rsu', () => {
        // Mock the RSU updateTableData thunk to prevent it from dispatching
        // async actions (like getRsuInfoOnly) during the snapshot test.
        // Without this, the thunk would cause side effects that make the snapshot
        // unstable or fail due to async state
        jest.spyOn(adminRsuTabSlice, 'updateTableData').mockImplementation(() => () => Promise.resolve());

        const { container } = render(
            <ThemeProvider theme={testTheme}>
                <Provider store={setupStore({ adminRsuTab: { loading: false, value: { activeDiv: 'rsu_table' } } })}>
                    <BrowserRouter>
                        <AdminFormManager activeForm={'add_rsu'} />
                    </BrowserRouter>
                </Provider>
            </ThemeProvider>
        );

        expect(replaceChaoticIds(container)).toMatchSnapshot();
    });

    it('snapshot user', () => {
        const { container } = render(
            <ThemeProvider theme={testTheme}>
                <Provider store={setupStore({})}>
                    <BrowserRouter>
                        <AdminFormManager activeForm={'add_user'} />
                    </BrowserRouter>
                </Provider>
            </ThemeProvider>
        );

        expect(replaceChaoticIds(container)).toMatchSnapshot();
    });

    it('snapshot organization', () => {
        const { container } = render(
            <ThemeProvider theme={testTheme}>
                <Provider store={setupStore({})}>
                    <BrowserRouter>
                        <AdminFormManager activeForm={'add_organization'} />
                    </BrowserRouter>
                </Provider>
            </ThemeProvider>
        )

        expect(replaceChaoticIds(container)).toMatchSnapshot()
    })
});
