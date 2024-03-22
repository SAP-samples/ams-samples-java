#!/usr/bin/env groovy
@Library(['piper-lib', 'cas-central-jenkins-library']) _

def repoOrg             = 'CPSecurity'
def repoName            = 'ams-samples-java'
def repoFullName            = repoOrg + '/' + repoName



def isBranchIndexingCause() {
    return currentBuild.getBuildCauses().toString().contains('BranchIndexingCause')
}

if (isBranchIndexingCause()) {
    // Aborts the build if triggered via Branch Indexing
    print "INFO: Build skipped due to trigger being Branch Indexing"
    currentBuild.result = 'ABORTED'
    return
}

properties([   
    buildDiscarder(
        logRotator(artifactDaysToKeepStr: '-1', artifactNumToKeepStr: '-1', daysToKeepStr: '-1', numToKeepStr: '10')
    )
])

try {
    node('ams-agent') {
             
        if (isTriggeredByUpstreamProject(upstreamProject: 'CPSecurity/cas-ams-dependency-builder/master')) {
            stage('Setup') {
                prepareScm()
            }
            
            def projectVersion = getMavenVersion()
            updateVersionAndPushChangesArtifactory treeish: treeish, artifactVersion: projectVersion, repoFullName: repoFullName

        } else {
            stage('Setup') {
                prepareScm()
            }
            stage('Unit Tests') {
                lock(resource: 'UNIT_TESTS', inversePrecedence: true) {
                    unitTests()
                }
            }

            stage('Integration Test') {
                integrationTests()
            }
        }
    }
} catch (Throwable err) { // catch all exceptions
    globalPipelineEnvironment.addError(this, err)
    throw err
} finally{
    node('ams-agent'){
        // At this point as the build is successful, we can clean the workspace
        cleanWorkspace()
    }
}

def getMavenVersion() {
    def mavenPom = readMavenPom (file: 'java-security-ams/pom.xml')
    def mavenVersion = mavenPom.getVersion().split("-", 2)[0]
    echo "Maven Version: ${mavenVersion}"
    return mavenVersion
}

// ===============================================
// Misc Utils
// ===============================================
def prepareScm() {
    deleteDir()
    checkout scm
}

def unitTests() {
    parallel(
        'Java': {
            dir('java-security-ams') {
                sh 'mvn -q clean test -U --settings ../.pipeline/maven-settings.xml -Dmaven.repo.local=${HOME}/.m2/java-security-ams/repository'
                // get results for the jenkins junit plugin
                junit 'target/surefire-reports/*.xml'
            }
        },
        'Spring': {
            dir('spring-security-ams') {
                sh 'mvn -q clean test -U --settings ../.pipeline/maven-settings.xml -Dmaven.repo.local=${HOME}/.m2/spring-security-ams/repository'
                // get results for the jenkins junit plugin
                junit 'target/surefire-reports/*.xml'
            }
        }
    )
}

def integrationTests() {
    if (env.BRANCH_NAME.startsWith('PR-')) {
        environment = 'dev'
        if (env.CHANGE_TARGET == 'master') {
            environment = 'cc3-qa-rot'
        }
        build job: 'CPSecurity/cas-ams-e2e-tests/master', parameters: [
            booleanParam(name: 'DEPLOY', value: true),
            booleanParam(name: 'TEST', value: true),
            booleanParam(name: 'NOTIFY', value: false),
            string(name: 'ENVIRONMENT', value: environment), 
            string(name: 'TREEISH_SAMPLES', value: env.CHANGE_BRANCH)
        ]
    }
}
