import React from "react";
import { render } from "@testing-library/react";
import RsuStatusDialog from "./RsuStatusDialog";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material";
import { testTheme } from "../../styles";
import { setupStore } from "../../store";
import { replaceChaoticIds } from "../../utils/test-utils";

it("should take a snapshot", () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { token: "" } } },
        })}
      >
        <RsuStatusDialog
          open={true}
          onClose={() => {}}
          rsuIp="10.0.0.180"
          token="test-token"
        />
      </Provider>
    </ThemeProvider>
  );

  expect(replaceChaoticIds(document.body)).toMatchSnapshot();
});
