INSERT INTO user_schema.tb_user (id, active, email, password)
VALUES (
           gen_random_uuid(),
           true,
           'admin@orderapi.com',
           '$2a$10$EblZqNptyYvcLm/VwDCVAuAw5QXFq7zN5.Cvk7IG4.m.7P4x.tFfm'
       );

INSERT INTO user_schema.tb_user_roles (user_id, role)
SELECT id, 'ADMIN'
FROM user_schema.tb_user
WHERE email = 'admin@orderapi.com';