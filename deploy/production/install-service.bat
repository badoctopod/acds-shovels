REM Initial variables
set APP_HOME=C:\Programm_Vist\acds\acds-shovels
set SERVICE_NAME=acds-shovels

REM Service definition
set PR_DISPLAYNAME=acds-shovels
set PR_DESCRIPTION=ACDS: API endpoint for shovel's HTTP client

REM Path to Apache Commons Daemon (procrun) executable
set PR_INSTALL=%APP_HOME%\bin\acds-shovels.exe

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=%APP_HOME%\logs
set PR_LOGLEVEL=Info

REM Path to java installation (jvm dll)
set PR_JVM=C:\Programm_Vist\acds\jdk\jre\bin\server\jvm.dll

REM Path to app jar artefact
set PR_CLASSPATH=%APP_HOME%\app\acds-shovels.jar

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=acds_shovels.core
set PR_STARTMETHOD=start

REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=acds_shovels.core
set PR_STOPMETHOD=stop

REM JVM configuration
set PR_JVMOPTIONS=^
-Xms128m;^
-Xmx128m;^
-XX:MaxMetaspaceSize=64m;^
-XX:+CMSClassUnloadingEnabled;^
-XX:+UseConcMarkSweepGC;^
-XX:+CMSParallelRemarkEnabled;^
-XX:+UseCMSInitiatingOccupancyOnly;^
-XX:CMSInitiatingOccupancyFraction=70;^
-XX:+CMSScavengeBeforeRemark;^
-Xloggc:%APP_HOME%\logs\gc.log;^
-XX:+PrintGC;^
-XX:+PrintReferenceGC;^
-XX:+PrintGCDetails;^
-XX:+PrintGCDateStamps;^
-XX:+PrintGCCause;^
-XX:+PrintTenuringDistribution;^
-XX:+UseGCLogFileRotation;^
-XX:NumberOfGCLogFiles=10;^
-XX:GCLogFileSize=10m;^
-Dconf=%APP_HOME%\conf\config.edn

REM Install service
%APP_HOME%\bin\acds-shovels.exe //IS//%SERVICE_NAME%

REM Start service
sc start %SERVICE_NAME%

REM Waiting for 10 seconds...
timeout 10 > NUL

REM Quering service state
sc queryex %SERVICE_NAME%

pause
