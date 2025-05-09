const { defineConfig } = require("cypress");

module.exports = defineConfig({
  e2e: {
    defaultCommandTimeout: 10000,
    specPattern:'cypress/E2ETesting/**/*.cy.{js,jsx,ts,tsx}',
    viewportHeight: 800,
    viewportWidth: 1200,
    baseUrl:'http://localhost:4200',
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
  },
});




