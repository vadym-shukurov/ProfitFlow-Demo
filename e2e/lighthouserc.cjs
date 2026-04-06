/** @type {import('@lhci/utils').LHCI.ServerCommand.Options} */
module.exports = {
  ci: {
    collect: {
      url: ['http://127.0.0.1:4200/login'],
      numberOfRuns: 1,
      settings: {
        onlyCategories: ['accessibility'],
        preset: 'desktop',
      },
    },
    assert: {
      assertions: {
        // Finance UX: enforce strong accessibility baseline (Lighthouse category score).
        'categories:accessibility': ['error', { minScore: 0.95 }],
      },
    },
    upload: {
      target: 'filesystem',
      outputDir: '.lighthouseci',
    },
  },
};
