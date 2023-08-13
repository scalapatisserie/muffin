import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';

import styles from './index.module.css';

const Button = (props) => (
  <div className={styles.buttons}>
    <Link
      className="button button--secondary button--lg"
      to={props.to}>
      {props.children}
    </Link>
  </div>
);


function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttonContainer}>
          <Button to="/getting-started/installation">Docs</Button>
          <Button to="https://github.com/little-inferno/muffin-original">Github</Button>
        </div>
        <div className={styles.badgeContainer}>
          <img alt="Build status" src="https://github.com/little-inferno/muffin/workflows/CI/badge.svg"/>
          <img alt="Maven Central" src="https://img.shields.io/maven-central/v/space.scalapatisserie/muffin-core_3"/>
        </div>

      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();

  return (
    <Layout
      title={siteConfig.title}
      description={siteConfig.tagline}>
      <HomepageHeader/>
      <main>
      </main>
    </Layout>
  );
}
