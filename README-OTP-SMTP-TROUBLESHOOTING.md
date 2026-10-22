# NEXORA OTP / SMTP troubleshooting

The login screenshot shows a `SocketTimeoutException` while connecting to `smtp.gmail.com:587`. This happens before Gmail can check the username/password. It usually means outbound SMTP is blocked by the current network/firewall, the host/port is unreachable, or the SMTP settings are incorrect; changing the OTP logic alone cannot fix a blocked TCP connection.

## Gmail SMTP setup

Configure these environment variables in the IntelliJ Run Configuration (Environment variables):

- `NEXORA_MAIL_HOST=smtp.gmail.com`
- `NEXORA_MAIL_PORT=587`
- `NEXORA_MAIL_USERNAME=your-full-gmail-address`
- `NEXORA_MAIL_PASSWORD=your-16-character-Google-app-password`
- `NEXORA_MAIL_FROM=your-full-gmail-address`
- `NEXORA_MAIL_STARTTLS=true`
- `NEXORA_MAIL_STARTTLS_REQUIRED=true`
- `NEXORA_MAIL_SSL=false`

Use a Google App Password (requires 2-Step Verification); do not use the normal Google account password. Do not commit credentials into source control.

If your network blocks port 587, try another trusted network/hotspot or a mail provider permitted by your network. Gmail also supports implicit SSL on port 465; to use it set `NEXORA_MAIL_PORT=465`, `NEXORA_MAIL_SSL=true`, `NEXORA_MAIL_STARTTLS=false`, and `NEXORA_MAIL_STARTTLS_REQUIRED=false`. Do not enable SSL and STARTTLS together.

## Check connectivity on Windows PowerShell

Run:

```powershell
Test-NetConnection smtp.gmail.com -Port 587
```

If `TcpTestSucceeded` is `False`, the Java application cannot reach that SMTP endpoint from the current network. Try port 465 with the matching SSL settings or contact the network administrator. If TCP succeeds but sending fails, check credentials, app password, sender address and Gmail account security settings.

## What changed in this repair

- Mail connection/read/write timeouts are configurable and default to 20 seconds.
- STARTTLS-required and implicit SSL settings are configurable, so port 587 and port 465 can be configured correctly.
- Spring `MailException` connection failures now produce a clear user-facing diagnostic instead of exposing a long nested Java exception.
- OTP authentication has not been bypassed. If SMTP is unreachable, no email OTP can be delivered; configure a reachable SMTP service before login can succeed.
