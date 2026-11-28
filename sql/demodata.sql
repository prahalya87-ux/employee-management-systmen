-- Demo data for the Employee Management System
-- Run AFTER database.sql. Safe to run more than once (duplicates are skipped).

USE employee_management;

-- Extra departments (HR, IT and Finance already exist from database.sql)
INSERT IGNORE INTO departments (dept_name, location) VALUES
  ('HR', 'Block A'),
  ('IT', 'Block B'),
  ('Finance', 'Block A'),
  ('Marketing', 'Block C'),
  ('Operations', 'Block C');

-- 12 employees: IDs 101 to 112 (Manager / Developer / Employee types)
INSERT IGNORE INTO employees
  (emp_id, name, age, gender, email, phone, dept_id, job_role, joining_date, basic_salary, emp_type) VALUES
(101, 'Adithya',    34, 'male', 'adithyac@company.com',  '9876543201', (SELECT dept_id FROM departments WHERE dept_name='HR'),         'HR Manager',          '2019-03-15', 75000.00, 'Manager'),
(102, 'Rahul Verma',      29, 'Male',   'rahul.verma@company.com',    '9876543202', (SELECT dept_id FROM departments WHERE dept_name='IT'),         'Senior Developer',    '2020-07-01', 68000.00, 'Developer'),
(103, 'Priya Nair',       26, 'Female', 'priya.nair@company.com',     '9876543203', (SELECT dept_id FROM departments WHERE dept_name='IT'),         'Software Developer',  '2022-01-10', 52000.00, 'Developer'),
(104, 'Karthik Raja',     41, 'Male',   'karthik.raja@company.com',   '9876543204', (SELECT dept_id FROM departments WHERE dept_name='IT'),         'IT Manager',          '2016-06-20', 95000.00, 'Manager'),
(105, 'Sneha Iyer',       31, 'Female', 'sneha.iyer@company.com',     '9876543205', (SELECT dept_id FROM departments WHERE dept_name='Finance'),    'Accountant',          '2021-04-05', 45000.00, 'Employee'),
(106, 'Arjun Mehta',      38, 'Male',   'arjun.mehta@company.com',    '9876543206', (SELECT dept_id FROM departments WHERE dept_name='Finance'),    'Finance Manager',     '2018-09-12', 88000.00, 'Manager'),
(107, 'Divya Krishnan',   24, 'Female', 'divya.krishnan@company.com', '9876543207', (SELECT dept_id FROM departments WHERE dept_name='HR'),         'HR Executive',        '2023-08-01', 32000.00, 'Employee'),
(108, 'Mohammed Faisal',  27, 'Male',   'faisal.m@company.com',       '9876543208', (SELECT dept_id FROM departments WHERE dept_name='IT'),         'Junior Developer',    '2024-02-19', 38000.00, 'Developer'),
(109, 'Lakshmi Menon',    45, 'Female', 'lakshmi.menon@company.com',  '9876543209', (SELECT dept_id FROM departments WHERE dept_name='Operations'), 'Operations Manager',  '2015-11-02', 82000.00, 'Manager'),
(110, 'Vikram Singh',     30, 'Male',   'vikram.singh@company.com',   '9876543210', (SELECT dept_id FROM departments WHERE dept_name='Marketing'),  'Marketing Executive', '2021-12-13', 41000.00, 'Employee'),
(111, 'Pooja Reddy',      28, 'Female', 'pooja.reddy@company.com',    '9876543211', (SELECT dept_id FROM departments WHERE dept_name='Marketing'),  'Content Writer',      '2022-10-24', 35000.00, 'Employee'),
(112, 'Sanjay Kumar',     33, 'Male',   'sanjay.kumar@company.com',   '9876543212', (SELECT dept_id FROM departments WHERE dept_name='Operations'), 'Logistics Coordinator','2020-05-18', 40000.00, 'Employee');

-- Full working-month attendance demo for October 2026 (weekends excluded).
-- INSERT IGNORE preserves any attendance that has already been edited.
INSERT IGNORE INTO attendance (emp_id, att_date, status)
SELECT e.emp_id, d.day,
       CASE WHEN (e.emp_id * 7 + DAY(d.day) * 3) % 11 = 0 THEN 'Absent' ELSE 'Present' END
FROM employees e
JOIN (
  SELECT DATE_ADD('2026-10-01', INTERVAL day_offset DAY) AS day
  FROM (
    SELECT 0 AS day_offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
    UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
    UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
  ) day_offsets
) d
WHERE e.emp_id BETWEEN 101 AND 112
  AND WEEKDAY(d.day) < 5;

-- Quick check
SELECT COUNT(*) AS employees_loaded FROM employees WHERE emp_id BETWEEN 101 AND 112;
SELECT COUNT(*) AS attendance_rows  FROM attendance;