# Security Policy

## Reporting a Vulnerability

> **Do NOT report security vulnerabilities through public GitHub issues.**

We take the security of EdgeClaw Mobile seriously. If you discover a security
vulnerability, please report it responsibly.

### How to Report

| Method | Contact |
|--------|--------|
| **Email** | [security@edgeclaw.dev](mailto:security@edgeclaw.dev) |
| **GitHub** | [Private Security Advisory](https://github.com/agentumi/edgeclaw_mobile/security/advisories/new) |

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Suggested fix (if any)

### Response Timeline

| Stage | Timeline |
|-------|----------|
| Acknowledgement | Within 48 hours |
| Initial Assessment | Within 1 week |
| Fix & Disclosure | Within 90 days |

We follow [Coordinated Vulnerability Disclosure](https://en.wikipedia.org/wiki/Coordinated_vulnerability_disclosure).

## Supported Versions

| Version | Supported |
|---------|----------|
| 0.1.x (latest) | Active support |
| < 0.1.0 | Not supported |

## Security Architecture

EdgeClaw Mobile implements a **zero-trust** security model where every
connection is authenticated, every message is encrypted, and every operation
is authorized.

### Cryptography Stack

| Layer | Algorithm | Purpose |
|-------|-----------|--------|
| **Identity** | Ed25519 | Device signing & authentication |
| **Key Exchange** | X25519 ECDH | Ephemeral key agreement |
| **Key Derivation** | HKDF-SHA256 | Session key derivation |
| **Encryption** | AES-256-GCM | Authenticated encryption |
| **Integrity** | SHA-256 | Hash-chained audit logs |
| **Anti-Replay** | Nonce + Timestamp | +/-30s tolerance per device |

### RBAC Policy Engine

4-tier role hierarchy with risk-level enforcement:

| Role | Risk Level | Scope |
|------|-----------|-------|
| Viewer | 0 (None) | Read-only status queries |
| Operator | 1 (Low) | File read, log view, network scan |
| Admin | 2 (Medium) | File write, process management |
| Owner | 3 (High) | Full system control |

### Security Invariants

These rules are **never** violated:

1. All connections are authenticated via Ed25519
2. All data is encrypted with AES-256-GCM
3. Nonces are never reused
4. Key material is zeroized after use
5. RBAC is enforced before every privileged operation
6. Audit logs are hash-chained for tamper detection

## Dependency Security

We use only well-audited cryptographic libraries:

| Crate | Purpose | Audit Status |
|-------|---------|-------------|
| `ed25519-dalek` | Ed25519 signatures | Audited |
| `x25519-dalek` | X25519 ECDH | Audited |
| `aes-gcm` | AES-256-GCM encryption | RustCrypto |
| `sha2` | SHA-256 hashing | RustCrypto |
| `hkdf` | HKDF key derivation | RustCrypto |

## Best Practices for Users

- Keep EdgeClaw updated to the latest version
- Rotate session keys regularly (default: 1 hour timeout)
- Apply principle of least privilege for RBAC roles
- Monitor audit logs for suspicious activity
- Never disable encryption (`require_encryption = true`)
- Use secure BLE pairing when connecting devices

## Scope

The following are **in scope** for security reports:

- Cryptographic implementation flaws
- Authentication or authorization bypass
- Nonce reuse or replay attacks
- Key material exposure
- RBAC policy bypass
- BLE pairing vulnerabilities

The following are **out of scope**:

- Denial of service (DoS) attacks
- Social engineering
- Issues in third-party dependencies (report upstream)

---

Thank you for helping keep EdgeClaw secure.
