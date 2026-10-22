# NEXORA Login troubleshooting

## Login flow

1. Enter either the SRM email username (for example `ns3476`), the full SRM email (`ns3476@srmist.edu.in`), or the official registration number (`RA2511003050xxx`).
2. Enter the NEXORA password.
3. NEXORA verifies the password before generating an OTP.
4. A 6-digit OTP is sent only to the registered SRM email.
5. The OTP expires in 5 minutes and is limited to 5 failed attempts.

## SMTP is required for real OTP delivery

Set these environment variables in IntelliJ Run/Debug Configuration:

- `NEXORA_MAIL_HOST`
- `NEXORA_MAIL_PORT` (normally `587`)
- `NEXORA_MAIL_USERNAME`
- `NEXORA_MAIL_PASSWORD`
- `NEXORA_MAIL_FROM`

Do not put an SRM student password into SMTP settings unless SRM explicitly provides an SMTP service account for this purpose.

If SMTP is missing or invalid, the login page now displays the actual configuration error instead of appearing to do nothing.
