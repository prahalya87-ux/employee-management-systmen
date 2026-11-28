# Employee Management System (Java + MySQL + JDBC)

## Setup
1. Install JDK 11+ and MySQL.
2. Run `sql/Database.sql` in MySQL Workbench (creates the `employee_management` database, base tables and sample departments). The web server creates the additional SmartHR tables and employee profile columns at startup.
3. Download the MySQL Connector/J jar (e.g. `mysql-connector-j-8.x.jar`) and put it in a `lib/` folder.
4. Configure `DB_URL`, `DB_USER`, and `DB_PASSWORD` in your environment. For local MySQL, the default URL and username are `localhost:3306/employee_management` and `root`; `DB_PASSWORD` defaults to an empty value.
5. Start the web server. On a new database, the first administrator is created as `admin` / `admin123`. If the demo employee Adithya (ID 101) exists, the server creates `adithya` / `adithya123` unless that employee already has a login. Sign in with a username; employee accounts can also sign in with their work email.

The server fills missing weekdays for the October 2026 demo attendance records without overwriting existing attendance. The Attendance page has a shortcut to inspect that demo month. Change the initial administrator password before using this application with real employee data. For new employee sign-in, set a username and password when creating the employee.

## Run from the terminal
Windows:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out;lib/*" src.WebServer
Mac/Linux:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out:lib/*" src.WebServer

Set your database environment variables before starting the server. On Windows PowerShell, for example:

    $env:DB_URL = "jdbc:mysql://localhost:3306/employee_management?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    $env:DB_USER = "root"
    $env:DB_PASSWORD = "<your local MySQL password>"
    .\run.bat

## Run in VS Code
Install the "Extension Pack for Java", open this folder, add the Connector/J jar under
"Referenced Libraries" (Java Projects panel), then run `Main.java`.

## Deploy publicly (Vercel + Render)

The Vercel site serves the static frontend and forwards `/api/*` requests to the Java API on Render. The Render Blueprint creates only the Free web service; the database must be a separate MySQL-compatible service. Choose a provider and plan explicitly marked free, and check its storage, connection, and usage limits. Render's Free service does not provide persistent MySQL storage.

1. Push this repository to GitHub.
2. Create a MySQL-compatible database with a provider whose free tier meets your needs, then import `sql/Database.sql`. Do not import `sql/demodata.sql` for real employee data.
3. In Render, create a **Blueprint** from this repository and use `render.yaml`. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` from your database provider. Choose a unique `ADMIN_USERNAME` and a strong `ADMIN_PASSWORD` of at least 12 characters. Keep credentials in Render's environment settings, not in source files.
4. Wait for the Render service to deploy and copy its HTTPS origin (for example, `https://employee-management-api.onrender.com`).
5. Import this same repository into Vercel. Use the repository root as the project root and set `RENDER_API_URL` to the Render HTTPS origin. Deploy; Vercel uses `vercel.json` to publish `web/` and the `/api/*` function to proxy requests.
6. Open the Vercel URL and sign in with the admin credentials you configured. Startup automatically creates the 12 demo employees and demo attendance/payroll data when they do not already exist.

If the database already contains an admin account, startup leaves its password unchanged; configure credentials before the first production startup. If a database password was ever committed to Git, rotate it with the database provider—removing it from the current source does not remove it from Git history.