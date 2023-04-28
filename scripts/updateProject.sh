PROJECT_NEW_VERSION=$1
ARTIFACTORY_USER=$2
ARTIFACTORY_PASSWORD=$3

PROJECT_CURRENT_VERSION=$(cat java-security-ams/pom.xml | grep "<version>.*</version>" | head -1 |awk -F'[><]' '{print $3}')
echo Updating samples project from $PROJECT_CURRENT_VERSION to $PROJECT_NEW_VERSION ...

JAVA_VERSION_OLD_LINE=$(cat java-security-ams/pom.xml | grep '<version>.*</version>' | head -1)
echo JAVA_VERSION_OLD_LINE: $JAVA_VERSION_OLD_LINE
export JAVA_VERSION_NEW_LINE=$(echo "<version>$PROJECT_NEW_VERSION</version>")
echo JAVA_VERSION_NEW_LINE: $JAVA_VERSION_NEW_LINE
find ./java-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$JAVA_VERSION_OLD_LINE=    $JAVA_VERSION_NEW_LINE=" {} \;

SPRING_VERSION_OLD_LINE=$(cat spring-security-ams/pom.xml | grep -m 2 '<version>.*</version>' | tail -1)
echo SPRING_VERSION_OLD_LINE: $SPRING_VERSION_OLD_LINE
export SPRING_VERSION_NEW_LINE=$(echo "<version>$PROJECT_NEW_VERSION</version>")
echo SPRING_VERSION_NEW_LINE: $SPRING_VERSION_NEW_LINE
find ./spring-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$SPRING_VERSION_OLD_LINE=    $SPRING_VERSION_NEW_LINE=" {} \;

if [[ "$ARTIFACTORY_USER" == "" ]] && [[ "$ARTIFACTORY_PASSWORD" == "" ]];
  then
    echo "Artifactory credentials are not passed -> It is okay!"
  else
    COMPILER_RELEASED_VERSION=$(curl -u $ARTIFACTORY_USER:$ARTIFACTORY_PASSWORD https://common.repositories.cloud.sap:443/artifactory/deploy.releases/com/sap/cloud/security/ams/dcl/com.sap.cloud.security.ams.dcl/maven-metadata.xml | grep "<latest>.*</latest>" | head -1 |awk -F'[><]' '{print $3}')
    
    JAVA_COMPILER_CURRENT_VERSION=$(cat java-security-ams/pom.xml | grep "<sap.cloud.security.ams.dcl>.*</sap.cloud.security.ams.dcl>" | head -1 |awk -F'[><]' '{print $3}')
    JAVA_COMPILER_VERSION_OLD_LINE=$(cat java-security-ams/pom.xml | grep '<sap.cloud.security.ams.dcl>.*</sap.cloud.security.ams.dcl>' | head -1)
    echo JAVA_COMPILER_VERSION_OLD_LINE: $JAVA_COMPILER_VERSION_OLD_LINE
    export JAVA_COMPILER_VERSION_NEW_LINE=$(echo "<sap.cloud.security.ams.dcl>$COMPILER_RELEASED_VERSION</sap.cloud.security.ams.dcl>")
    echo JAVA_COMPILER_VERSION_NEW_LINE: $JAVA_COMPILER_VERSION_NEW_LINE
    echo Updating java compiler from $JAVA_COMPILER_CURRENT_VERSION to $COMPILER_RELEASED_VERSION ...
    find ./java-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$JAVA_COMPILER_VERSION_OLD_LINE=        $JAVA_COMPILER_VERSION_NEW_LINE=" {} \;

    SPRING_COMPILER_CURRENT_VERSION=$(cat spring-security-ams/pom.xml | grep "<sap.cloud.security.ams.dcl>.*</sap.cloud.security.ams.dcl>" | head -1 |awk -F'[><]' '{print $3}')
    SPRING_COMPILER_VERSION_OLD_LINE=$(cat spring-security-ams/pom.xml | grep '<sap.cloud.security.ams.dcl>.*</sap.cloud.security.ams.dcl>' | head -1)
    echo SPRING_COMPILER_VERSION_OLD_LINE: $SPRING_COMPILER_VERSION_OLD_LINE
    export SPRING_COMPILER_VERSION_NEW_LINE=$(echo "<sap.cloud.security.ams.dcl>$COMPILER_RELEASED_VERSION</sap.cloud.security.ams.dcl>")
    echo SPRING_COMPILER_VERSION_NEW_LINE: $SPRING_COMPILER_VERSION_NEW_LINE
    echo Updating spring compiler from $SPRING_COMPILER_CURRENT_VERSION to $COMPILER_RELEASED_VERSION ...
    find ./spring-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$SPRING_COMPILER_VERSION_OLD_LINE=        $SPRING_COMPILER_VERSION_NEW_LINE=" {} \;

    CLIENT_LIBS_RELEASED_VERSION=$(curl -u $ARTIFACTORY_USER:$ARTIFACTORY_PASSWORD https://common.repositories.cloud.sap:443/artifactory/deploy.releases/com/sap/cloud/security/ams/client/java-ams/maven-metadata.xml | grep "<latest>.*</latest>" | head -1 |awk -F'[><]' '{print $3}')

    JAVA_CLIENT_LIBS_CURRENT_VERSION=$(cat java-security-ams/pom.xml | grep "<sap.cloud.security.ams.version>.*</sap.cloud.security.ams.version>" | head -1 |awk -F'[><]' '{print $3}')
    JAVA_CLIENT_LIBS_VERSION_OLD_LINE=$(cat java-security-ams/pom.xml | grep '<sap.cloud.security.ams.version>.*</sap.cloud.security.ams.version>' | head -1)
    echo JAVA_CLIENT_LIBS_VERSION_OLD_LINE: $JAVA_CLIENT_LIBS_VERSION_OLD_LINE
    export JAVA_CLIENT_LIBS_VERSION_NEW_LINE=$(echo "<sap.cloud.security.ams.version>$CLIENT_LIBS_RELEASED_VERSION</sap.cloud.security.ams.version>")
    echo JAVA_CLIENT_LIBS_VERSION_NEW_LINE: $JAVA_CLIENT_LIBS_VERSION_NEW_LINE
    echo Updating java compiler from $JAVA_CLIENT_LIBS_CURRENT_VERSION to $CLIENT_LIBS_RELEASED_VERSION ...
    find ./java-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$JAVA_CLIENT_LIBS_VERSION_OLD_LINE=        $JAVA_CLIENT_LIBS_VERSION_NEW_LINE=" {} \;

    SPRING_CLIENT_LIBS_CURRENT_VERSION=$(cat spring-security-ams/pom.xml | grep "<sap.cloud.security.ams.version>.*</sap.cloud.security.ams.version>" | head -1 |awk -F'[><]' '{print $3}')
    SPRING_CLIENT_LIBS_VERSION_OLD_LINE=$(cat spring-security-ams/pom.xml | grep '<sap.cloud.security.ams.version>.*</sap.cloud.security.ams.version>' | head -1)
    echo SPRING_CLIENT_LIBS_VERSION_OLD_LINE: $SPRING_CLIENT_LIBS_VERSION_OLD_LINE
    export SPRING_CLIENT_LIBS_VERSION_NEW_LINE=$(echo "<sap.cloud.security.ams.version>$CLIENT_LIBS_RELEASED_VERSION</sap.cloud.security.ams.version>")
    echo SPRING_CLIENT_LIBS_VERSION_NEW_LINE: $SPRING_CLIENT_LIBS_VERSION_NEW_LINE
    echo Updating spring compiler from $SPRING_CLIENT_LIBS_CURRENT_VERSION to $CLIENT_LIBS_RELEASED_VERSION ...
    find ./spring-security-ams -name 'pom.xml' -exec sed -i '' -e "s=$SPRING_CLIENT_LIBS_VERSION_OLD_LINE=        $SPRING_CLIENT_LIBS_VERSION_NEW_LINE=" {} \;
fi

git status
git add -u

count=$(git diff --cached --numstat | wc -l)
if [ $count -gt 0 ]
then
    git commit -m "Set project version: $PROJECT_NEW_VERSION"
fi