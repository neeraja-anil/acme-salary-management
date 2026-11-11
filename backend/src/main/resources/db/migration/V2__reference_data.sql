-- Reference data: currencies, countries, departments and job titles.
-- FX rates are fixed, illustrative rates used for reporting comparisons only (see requirements.md, section 4).

INSERT INTO fx_rate (currency_code, rate_to_usd, as_of_date) VALUES
    ('USD', '1.0000', '2026-01-01'),
    ('GBP', '1.3400', '2026-01-01'),
    ('EUR', '1.1700', '2026-01-01'),
    ('INR', '0.0113', '2026-01-01'),
    ('CAD', '0.7200', '2026-01-01'),
    ('SGD', '0.7800', '2026-01-01'),
    ('AUD', '0.6600', '2026-01-01');

INSERT INTO country (code, name, currency_code) VALUES
    ('US', 'United States',  'USD'),
    ('GB', 'United Kingdom', 'GBP'),
    ('DE', 'Germany',        'EUR'),
    ('FR', 'France',         'EUR'),
    ('IN', 'India',          'INR'),
    ('CA', 'Canada',         'CAD'),
    ('SG', 'Singapore',      'SGD'),
    ('AU', 'Australia',      'AUD');

INSERT INTO department (id, name) VALUES
    (1,  'Engineering'),
    (2,  'Product'),
    (3,  'Design'),
    (4,  'Sales'),
    (5,  'Marketing'),
    (6,  'Customer Support'),
    (7,  'Finance'),
    (8,  'Human Resources'),
    (9,  'Operations'),
    (10, 'Legal');

INSERT INTO job_title (name, department_id) VALUES
    ('Software Engineer',                 1),
    ('QA Engineer',                       1),
    ('DevOps Engineer',                   1),
    ('Data Engineer',                     1),
    ('Engineering Manager',               1),
    ('Product Manager',                   2),
    ('Product Analyst',                   2),
    ('Product Designer',                  3),
    ('UX Researcher',                     3),
    ('Account Executive',                 4),
    ('Sales Development Representative',  4),
    ('Sales Manager',                     4),
    ('Marketing Specialist',              5),
    ('Content Strategist',                5),
    ('Marketing Manager',                 5),
    ('Support Specialist',                6),
    ('Support Team Lead',                 6),
    ('Financial Analyst',                 7),
    ('Accountant',                        7),
    ('Finance Manager',                   7),
    ('HR Generalist',                     8),
    ('Recruiter',                         8),
    ('HR Business Partner',               8),
    ('Operations Analyst',                9),
    ('Office Manager',                    9),
    ('Legal Counsel',                     10),
    ('Paralegal',                         10);
