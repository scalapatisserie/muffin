// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Muffin',
  tagline: 'Scala framework for your mattermost bots',
  favicon: 'img/logo.png',

  url: 'https://little-inferno.github.io',

  baseUrl: '/muffin/',

  organizationName: 'scalapatisserie',
  projectName: 'muffin',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'ru'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: "../docs",
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
  /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/logo.png',
      navbar: {
        logo: {
          alt: 'Muffin logo',
          src: 'img/logo.png',
        },
        title: 'Muffin',
        items: [
          // {
          //   type: 'localeDropdown',
          //   position: 'right',
          // },
          {
            href: 'https://github.com/little-inferno/muffin-original',
            position: 'right',
            className: 'header-github-link',
            'aria-label': 'github repository',
          }
        ],
      },
      footer: {
        style: 'light',
        copyright: `Copyright © 2022 - ${new Date().getFullYear()} Muffin<br>Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
      },
    }),
};

module.exports = config;
