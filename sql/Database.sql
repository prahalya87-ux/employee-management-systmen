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