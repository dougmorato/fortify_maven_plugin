================================================================
          Sofware Name: sca-maven-plugin - ver. 4.20
================================================================

[ Software Name ]         sca-maven-plugin
[ Version ]               4.20
[ Organization ]          HP Enterprise Security Products
[ Organization URL ]      http://www.hpenterprisesecurity.com
[ Build Environment ]     JDK 1.6.0_37, Maven 3.0.5
[ Operation Environment ] Same as HP Fortify v4.20
[ Last Modified ]         2014-04-01

----------------------------------------------------------------

<< Introduction >>
   sca-maven-plugin is a maven-plugin for providing sca's clean, translation, scan and upload functionality.
   
<< System Operating Environment >>
   sca-maven-plugin supports Maven 2.0.11, 2.2.1 and 3.0.5.
   
<< Preparation >>
   For Maven 2.0.11 or 2.2.1, you need to modify TranslationMojo.java as follows.
   
    16: import org.apache.maven.plugin.PluginManager:           // For Maven 2.0 and 2.2
    17: //import org.apache.maven.plugin.BuildPluginManager;    // For Maven 3.0
   
   242:     private PluginManager pluginManager;        // For Maven 2.0 and 2.2
   243: //  private BuildPluginManager pluginManager;   // For Maven 3.0
   
   For Maven 3.0.5, you don't need to modify the source file.

<< Installation >>
   To install the package into the local repository, for use as a dependency in other projects locally:
   
   1. If you already have the package
   
   $mvn install
   
   2. If you don't have the package
   
   $mvn clean package install

<< Uninstallation >>
   To unisntall the package from the local repository, please delete sca-maven-plugin from the local repository manually.

<< Usage >>
   There are two usages. For detail, please refer to javadoc in target/site.
   
  << Usage 1>>
     Use as maven-plugin.
     
     Install target application in the local repository:
        $mvn install
     Clean:
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<ver>:clean
        or
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:clean
        or
        $mvn sca:clean
     Translate:
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<ver>:translate
        or
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:translate
        or
        $mvn sca:translate
     Scan:
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<ver>:scan
        or
        $mvn com.fortify.ps.maven.plugin:sca-maven-plugin:scan
        or
        $mvn sca:scan
     
     Note1: If you don't specify <ver>, maven always call the latest version of sca-maven-plugin in the local repository.
     Note2: sca-maven-plugin searchs jar file from the local repository and try to resolve classes in your application. 
            So if maven project is multiple project, please install your project before executing sca-maven-plugin.
     Note3: If you want to use short goal name, please put setting.xml in the local repository.
            So you can execute sca-maven-plugin as follows.
            $mvn sca:translate
   
  << Usage 2>>
     Use Maven Integration feature like Ant Integration.
     SCA provides various build integration such as Ant Integration, make Integration, devenv integration and so on.
     You can also use sca-maven-plugin as follows.
     
     Install target application in the local repository:
        $mvn install
     Clean:
        $sourceanalyzer -b <build id> -clean
     Translate:
        $sourceanalyzer -b <build id> [sca build options] mvn
        or
        $sourceanalyzer -b <build id> [sca build options] mvn com.fortify.ps.maven.plugin:sca-maven-plugin:<ver>:translate
        or
        $sourceanalyzer -b <build id> [sca build options] mvn com.fortify.ps.maven.plugin:sca-maven-plugin:translate
        or
        $sourceanalyzer -b <build id> [sca build options] mvn sca:translate
     Scan:
        $sourceanalyzer -b <build id> [sca scan options] -scan -f result.fpr
     
     Note1: In this usage, Maven Integration only supports translation.
     Note2: sca-maven-plugin searchs jar file from the local repository and try to resolve classes. 
            So if maven project is multiple project, please install your project before executing sca-maven-plugin.
   
<< Samples >>
   The tests can be run on any projects that use Maven.
   (For instance those included in the samples directory, or WebGoat 5.3: http://code.google.com/p/webgoat/)
