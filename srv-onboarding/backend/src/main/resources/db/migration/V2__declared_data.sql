-- Datos que el usuario escribe en la app; se comparan contra los leidos del PDF417.
ALTER TABLE onboarding_session
    ADD COLUMN declared_document_number VARCHAR(30),
    ADD COLUMN declared_first_names     VARCHAR(160),
    ADD COLUMN declared_last_names      VARCHAR(160),
    ADD COLUMN declared_birth_date      DATE;
