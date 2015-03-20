 This is a maven packaging of the EightBall sample.  It can be used to verify
 correct functioning of the maven plugin or provide useful feedback in case 
 of issues.
 
 This example requires pre-existing installations of
    F360 Analyzers
    Maven 2.0.9 or higher.
    
 To create an fpr file for this project do the following
 
 1. Add the directory containing the sourceanalyzer executable to the path
 
    e.g. export set PATH=$PATH:/path/to/f360/bin or
         set PATH=%PATH%;path\to\f360\bin
         
 2. Verify that the path has been set correctly by running
    sourceanalyzer -h 
    
 3. In the Eightball directory run
 
    mvn clean
    mvn package
    
 4. If 3 was successful generate an fpr by running the following commands
     
    mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<version>:clean
    
    mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<version>:translate -Dfortify.sca.verbose=true -Dfortify.sca.debug=true
    
    mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<version>:scan -Dfortify.sca.verbose=true -Dfortify.sca.debug=true
    
5. The fpr file and interim log files useful for trouble shooting will be in

   target/EightBall-1.0.fpr 
