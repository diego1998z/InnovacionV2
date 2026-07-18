INSERT INTO users (username, password, full_name, email, role, active, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Administrador del Sistema',
    'admin@creditai.pe',
    'ADMIN',
    true,
    NOW(), NOW()
);

INSERT INTO users (username, password, full_name, email, role, active, created_at, updated_at)
VALUES (
    'analista',
    '$2a$10$slYQmyNdgTY18LpSsvpHBOidH9TSskTogQi9aun0JWbUFZSKCCYIW',
    'Ana García López',
    'agarcia@creditai.pe',
    'ANALYST',
    true,
    NOW(), NOW()
);

INSERT INTO clients (dni, full_name, age, address, phone, email, monthly_income, total_savings, current_debts, status, created_at, updated_at)
VALUES
('12345678', 'Carlos Mendoza Ríos',       35, 'Av. Javier Prado 1234, Miraflores', '987654321', 'cmendoza@email.com',  7500.00, 25000.00, 0.00,    'ACTIVE', NOW(), NOW()),
('23456789', 'María Flores Gutiérrez',    28, 'Jr. Camaná 456, Lima Centro',        '976543210', 'mflores@email.com',   3200.00, 8000.00,  1500.00, 'ACTIVE', NOW(), NOW()),
('34567890', 'Juan Torres Vargas',        45, 'Calle Las Flores 789, Surco',         '965432109', 'jtorres@email.com',   12000.00,45000.00, 5000.00, 'ACTIVE', NOW(), NOW()),
('45678901', 'Rosa Quispe Mamani',        22, 'Av. Túpac Amaru 321, Comas',          '954321098', 'rquispe@email.com',   1800.00, 1200.00,  800.00,  'ACTIVE', NOW(), NOW()),
('56789012', 'Pedro Castillo Huanca',     52, 'Psje. Los Girasoles 12, La Molina',   '943210987', 'pcastillo@email.com', 9500.00, 60000.00, 0.00,    'ACTIVE', NOW(), NOW());

INSERT INTO financial_history (client_id, record_type, amount, description, record_date, payment_status, created_at)
SELECT id, 'PAYMENT', 850.00, 'Cuota préstamo vehicular', '2024-01-15', 'ON_TIME', NOW() FROM clients WHERE dni = '12345678';
INSERT INTO financial_history (client_id, record_type, amount, description, record_date, payment_status, created_at)
SELECT id, 'PAYMENT', 850.00, 'Cuota préstamo vehicular', '2024-02-15', 'ON_TIME', NOW() FROM clients WHERE dni = '12345678';
INSERT INTO financial_history (client_id, record_type, amount, description, record_date, payment_status, created_at)
SELECT id, 'CREDIT_PRODUCT', 15000.00, 'Préstamo vehicular', '2023-06-01', 'ON_TIME', NOW() FROM clients WHERE dni = '12345678';

INSERT INTO financial_history (client_id, record_type, amount, description, record_date, payment_status, overdue_amount, created_at)
SELECT id, 'PAYMENT', 200.00, 'Cuota microcrédito', '2024-01-10', 'OVERDUE', 200.00, NOW() FROM clients WHERE dni = '45678901';
INSERT INTO financial_history (client_id, record_type, amount, description, record_date, payment_status, created_at)
SELECT id, 'CREDIT_PRODUCT', 2000.00, 'Microcrédito emprendimiento', '2023-10-01', 'LATE', NOW() FROM clients WHERE dni = '45678901';
