# Security and responsible reporting

Home Monitor is an early public beta. Do **not** post passwords, admin tokens, API keys, private logs, personal calendar/to-do content or sensitive network details in public GitHub Issues.

## Trust boundaries

- Web admin uses token authentication but is served via **HTTP** and binds to the LAN while the app is active. It is not suitable for exposed/public networks.
- Never configure router port-forwarding or public tunnels to expose TCP `8765`.
- The optional WF1 proxy should be self-hosted and protected with a strong bearer token. Never expose the owner/developer's upstream monitor or control endpoints.
- ADB is for advanced debugging, not normal installation. Disable network debugging when it is not needed.

## Reporting

Open a GitHub Issue only for non-sensitive security questions or symptoms. If the report includes a working exploit, active credential, private data or details that would expose other users, **do not publish it publicly**. Contact the maintainer using a private channel if one is listed on the maintainer's GitHub profile. If no private contact method is available, report that a private reporting channel is needed without disclosing exploit details.

No formal vulnerability response SLA is offered during this early beta.
