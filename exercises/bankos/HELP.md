# Getting Started

### Reference Documentation

## Kafka Security
- **TLS** — encrypts data in transit (nobody can sniff payment events on the network)
- **SASL** — authenticates identity (only known services can connect to the broker)
- **ACLs** — authorizes access (payment-service can produce to `payment-events` but cannot read from `audit-events`)
---
The relationship between them:
```
TLS       → answers: is the connection encrypted?
SASL      → answers: who are you?
ACLs      → answers: what are you allowed to do?
```
