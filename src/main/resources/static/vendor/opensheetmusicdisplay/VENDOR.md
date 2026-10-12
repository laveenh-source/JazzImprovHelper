# OpenSheetMusicDisplay (vendored)

- Project: https://github.com/opensheetmusicdisplay/opensheetmusicdisplay (BSD-3-Clause, see LICENSE)
- Version: 2.2.1
- Taken from: https://registry.npmjs.org/opensheetmusicdisplay/-/opensheetmusicdisplay-2.2.1.tgz (`build/opensheetmusicdisplay.min.js`, unmodified)
- npm integrity (verified before copying): sha512-2DqI2lQKKzDDoIe7yD8yNyWTofSdRbFZYTD3nGRzEo1XmTK2DJWcUXLRXZ2E5xzOt7Pd+RmSGL1quFQZRCqDMw==
- sha256 of the copied file: 224ee5bee61d31ff3d07ade025f931a6cecd394cb54d2e39b1e7af2f0c7f1f5d

It is committed instead of loaded from a CDN so the page works offline and in docker compose, the content
security policy can forbid third-party scripts, and a CDN outage or compromise cannot affect the site.
To update: download the new release from npm, check its integrity hash, replace the file and this note.
