# Security Policy

Sigverage is a privacy-first, fully on-device application: there are no accounts, no
backends, and no telemetry. All recorded location and cellular data stays in a local
SQLite database on your device and never leaves it unless you explicitly export it.

## Supported Versions

Security fixes are applied to the latest release. Older releases are supported on a
best-effort basis as shown below.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

Please report security vulnerabilities privately — do **not** open a public issue.

- **Email**: [security@sigverage.app](mailto:security@sigverage.app)
- **Subject**: `[Security] <short description>`

Please include:

- A description of the vulnerability and its potential impact
- The affected version(s) and device/OS details
- Steps to reproduce, or a minimal proof of concept
- Any suggested fix, if you have one

You will receive an acknowledgement within 48 hours and a status update at least every
7 days until the issue is resolved. If the vulnerability is confirmed, a fix will be
released as soon as possible and you may be credited (unless you prefer to remain
anonymous).

## Scope

The following are considered in scope:

- Vulnerabilities in the Sigverage application codebase
- Insecure handling of local data (e.g., improper storage of location or cellular
  records, unsafe CSV import/export paths)

The following are **out of scope**:

- Issues in third-party libraries or the Android platform itself (please report those
  to their respective maintainers)
- Social engineering, phishing, or physical-access attacks
- Features or behaviors that require a rooted device or modified firmware

## Security Practices

- All data is stored locally; there is no network communication in the app beyond
  map tile downloads from OpenStreetMap servers
- CSV export includes formula-injection protection (RFC-4180 compliant quoting)
- Data can be purged manually or auto-expired via a user-configurable retention policy
