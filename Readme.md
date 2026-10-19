# Employee Management System (Java + MySQL + JDBC)

## Setup
1. Install JDK 11+ and MySQL.
2. Run `sql/database.sql` in MySQL Workbench (creates the `employee_management` database, tables and 3 sample departments).
3. Download the MySQL Connector/J jar (e.g. `mysql-connector-j-8.x.jar`) and put it in a `lib/` folder.
4. Open `src/DatabaseConnection.java` and set `USER` and `PASSWORD`.

## Run from the terminal
Windows:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out;lib/*" Main
Mac/Linux:
    javac -cp "lib/*" -d out src/*.java
    java -cp "out:lib/*" Main

## Run in VS Code
Install the "Extension Pack for Java", open this folder, add the Connector/J jar under
"Referenced Libraries" (Java Projects panel), then run `Main.java`.