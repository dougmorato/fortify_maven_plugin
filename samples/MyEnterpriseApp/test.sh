echo Using `which sourceanalyzer`
mvn clean
mvn compile package
mvn sca:clean
mvn sca:ear | tee sca.build.log
mvn -P debug sca:translate | tee -a sca.build.output
mvn sca:scan
mvn sca:cleanUpGeneratedSources
