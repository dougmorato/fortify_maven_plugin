call mvn clean
call mvn compile package
call mvn sca:clean
call mvn sca:ear
call mvn sca:translate
call mvn sca:cleanUpGeneratedSources
call mvn sca:scan
