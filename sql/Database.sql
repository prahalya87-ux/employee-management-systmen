-- Employee Management System - database script
-- Run this once in MySQL Workbench or the mysql command line.

CREATE DATABASE IF NOT EXISTS employee_management;
USE employee_management;

CREATE TABLE IF NOT EXISTS departments (
    dept_id     INT AUTO_INCREMENT PRIMARY KEY,
    dept_name   VARCHAR(50) NOT NULL UNIQUE,
    location    VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS employees (
    emp_id        INT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    age           INT NOT NULL,
    gender        VARCHAR(10) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(15) NOT NULL,
    dept_id       INT NULL,
    job_role      VARCHAR(50) NOT NULL,
    joining_date  DATE NOT NULL,
    basic_salary  DECIMAL(10,2) NOT NULL,
    emp_type      VARCHAR(20) NOT NULL DEFAULT 'Employee',  -- Employee / Manager / Developer
    FOREIGN KEY (dept_id) REFERENCES departments(dept_id)
);

CREATE TABLE IF NOT EXISTS attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id        INT NOT NULL,
    att_date      DATE NOT NULL,
    status        ENUM('Present','Absent') NOT NULL,
    UNIQUE (emp_id, att_date),            -- one record per employee per day
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE
);

-- HR profile and authentication fields
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL,
    ADD COLUMN IF NOT EXISTS address VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS employment_status ENUM('Active','Inactive','On Leave') NOT NULL DEFAULT 'Active',
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id INT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','EMPLOYEE') NOT NULL,
    last_login TIMESTAMP NULL,
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS leave_requests (
    leave_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id INT NOT NULL,
    leave_type ENUM('Casual Leave','Sick Leave','Earned Leave') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    decided_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS leave_balances (
    emp_id INT NOT NULL,
    leave_type ENUM('Casual Leave','Sick Leave','Earned Leave') NOT NULL,
    allocated_days INT NOT NULL,
    used_days INT NOT NULL DEFAULT 0,
    PRIMARY KEY (emp_id, leave_type),
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS salary_slips (
    slip_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id INT NOT NULL,
    salary_month TINYINT NOT NULL,
    salary_year SMALLINT NOT NULL,
    basic_salary DECIMAL(10,2) NOT NULL,
    hra DECIMAL(10,2) NOT NULL,
    allowances DECIMAL(10,2) NOT NULL DEFAULT 0,
    bonus DECIMAL(10,2) NOT NULL,
    deductions DECIMAL(10,2) NOT NULL,
    gross_salary DECIMAL(10,2) NOT NULL,
    net_salary DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (emp_id, salary_month, salary_year),
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS salary_records (
    record_id         INT AUTO_INCREMENT PRIMARY KEY,
    emp_id            INT NOT NULL,
    salary_month      INT NOT NULL,
    salary_year       INT NOT NULL,
    basic_salary      DECIMAL(10,2) NOT NULL,
    hra               DECIMAL(10,2) NOT NULL,
    da                DECIMAL(10,2) NOT NULL,
    bonus             DECIMAL(10,2) NOT NULL,
    gross_salary      DECIMAL(10,2) NOT NULL,
    pf                DECIMAL(10,2) NOT NULL,
    tax               DECIMAL(10,2) NOT NULL,
    absent_deduction  DECIMAL(10,2) NOT NULL,
    total_deductions  DECIMAL(10,2) NOT NULL,
    net_salary        DECIMAL(10,2) NOT NULL,
    absent_days       INT NOT NULL,
    UNIQUE (emp_id, salary_month, salary_year),
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE
);

-- Optional sample data
INSERT IGNORE INTO departments (dept_name, location) VALUES
  ('HR', 'Block A'), ('IT', 'Block B'), ('Finance', 'Block A');