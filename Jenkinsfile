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
            stage('visualize tests') {
                junit 'java-security-ams/target/surefire-reports/*.xml'
                junit 'spring-security-ams/target/surefire-reports/*.xml'
            }
            stage('Integration Test') {
                integrationTests()
            }
        }
    }
} catch (Throwable err) { // catch all exceptions
    globalPipelineEnvironment.addError(this, err)
    throw err
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
                sh 'mvn -q clean test'
            }
        },
        'Spring': {
            dir('spring-security-ams') {
                sh 'mvn -q clean test'
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
