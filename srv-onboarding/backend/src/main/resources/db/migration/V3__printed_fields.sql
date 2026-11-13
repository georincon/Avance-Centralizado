-- Datos impresos en el reverso de la cedula (no vienen en el codigo de barras).
ALTER TABLE onboarding_session
    ADD COLUMN birth_city       VARCHAR(80),
    ADD COLUMN birth_department VARCHAR(80),
    ADD COLUMN height_cm        INTEGER;
