$env:JAVA_HOME='E:\Work\jdk\jdk-17.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Set-Location 'E:\WorkTree\Agent-xiaoAI\backend'
& mvn 'spring-boot:run' '-Dspring-boot.run.profiles=mvp' '-DskipTests'
