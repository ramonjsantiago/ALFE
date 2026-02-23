# Phase 6.5.0: Headless regression check runner (PowerShell)

mvn -q -DskipTests package
java -cp target/classes com.fileexplorer.tools.RegressionCheckMain
