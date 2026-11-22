import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { Theme } from "@astryxdesign/core/theme";
import "@astryxdesign/core/reset.css";
import "@astryxdesign/core/astryx.css";
import "@fontsource-variable/manrope";
import { themes } from "./astryx-themes.js";
import { App } from "./App";
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Theme theme={themes["field-journal"]} mode="light">
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Theme>
  </React.StrictMode>,
);
