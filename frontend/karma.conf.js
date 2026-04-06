// Karma configuration — merged with Angular CLI defaults. See:
// https://angular.dev/reference/configs/karma-config

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-spec-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {
        // Fail specs that never call expect() — catches dead or incomplete tests.
        failOnEmptyTest: true,
      },
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        { type: 'lcovonly' },
      ],
      check: {
        global: {
          // Industrial gate: ≥85% line/statement/function; branch threshold slightly
          // lower because template/UI branching is harder to cover exhaustively.
          statements: 85,
          branches: 70,
          functions: 85,
          lines: 85,
        },
      },
    },
    reporters: ['spec', 'progress', 'kjhtml'],
    browsers: ['Chrome'],
    restartOnFileChange: true,
  });
};
