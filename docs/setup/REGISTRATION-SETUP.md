# NEXORA Public Student Registration

NEXORA now supports public student registration without pre-creating the student in MySQL.

## Flow

1. Student opens `/signup`.
2. Student enters an official `@srmist.edu.in` email and profile details.
3. NEXORA validates the registration data.
4. NEXORA generates a cryptographically random 6-digit OTP.
5. The OTP is stored only as a password hash in `pending_student_registrations` and expires after 5 minutes.
6. The OTP is emailed to the supplied SRMIST address.
7. Student enters the OTP.
8. Only after successful verification does NEXORA insert the student into the `students` table.
9. The student can immediately log in using registration number or SRMIST email and password.
10. The pending record is deleted after successful account creation.

## MySQL Workbench

No manual student INSERT is required. Workbench is only used to inspect the database.

On application startup, NEXORA creates `pending_student_registrations` automatically if it does not exist.

## Required configuration

Set the MySQL variables and SMTP variables from `.env.example` in the environment used to start Spring Boot.

SMTP is intentionally configurable rather than hard-coding an SRM mail server that NEXORA does not control. Use the SMTP service/credentials authorized for your SRMIST account or deployment.

## Security behavior

- Only addresses ending in `@srmist.edu.in` can start registration.
- Passwords are hashed with the application's configured `PasswordEncoder`.
- OTPs are hashed before storage.
- OTPs expire after 5 minutes.
- Five incorrect OTP attempts invalidate the pending registration.
- Existing registration numbers/emails cannot create another student account.
- A failed email send removes the pending registration so the user is not left with an unusable registration.

## Existing students

Existing rows in `students` are not modified. They can continue to log in normally.


## Login OTP

Students can log in using either their registration number (for example `RA2511003050xxx`) or their SRM email ID. Both `ns3476` and `ns3476@srmist.edu.in` are accepted. After the password is verified, NEXORA sends a 6-digit OTP to the student's stored SRM email. The login OTP expires after 5 minutes and is stored only as a hash.
