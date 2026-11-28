# Employee Management System (Java + MySQL + JDBC)

## Setup
1. Install JDK 11+ and MySQL.
2. Run `sql/Database.sql` in MySQL Workbench (creates the `employee_management` database, base tables and sample departments). The web server creates the additional SmartHR tables and employee profile columns at startup.
3. Download the MySQL Connector/J jar (e.g. `mysql-connector-j-8.x.jar`) and put it in a `lib/` folder.
4. Open `src/DatabaseConnection.java` and set `USER` and `PASSWORD`.
5. Start the web server. On a new database, the first administrator is created as `admin` / `admin123`. If the demo employee Adithya (ID 101) exists, the server creates `adithya` / `adithya123` unless that employee already has a login. Sign in with a username; employee accounts can also sign in with their work email.

The server fills missing weekdays for the October 2026 demo attendance records without overwriting existing attendance. The Attendance page has a shortcut to inspect that demo month. Change the initial administrator password before using this application with real employee data. For new employee sign-in, set a username and password when creating the employee.

## Run from the terminal
Windows:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out;lib/*" src.WebServer
Mac/Linux:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out:lib/*" src.WebServer

## Run in VS Code
Install the "Extension Pack for Java", open this folder, add the Connector/J jar under
"Referenced Libraries" (Java Projects panel), then run `Main.java`.