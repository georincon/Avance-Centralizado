-- Datos de contacto del ciudadano (correo electrónico y celular): se pueden compartir en el PID (email / phone_number).
-- Idempotente. No toca los datos existentes: las columnas nuevas quedan vacías hasta que se llenen desde el panel.
ALTER TABLE cedulas ADD COLUMN IF NOT EXISTS correo_electronico VARCHAR(255);
ALTER TABLE cedulas ADD COLUMN IF NOT EXISTS celular VARCHAR(10);
