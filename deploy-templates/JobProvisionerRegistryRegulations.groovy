import groovy.json.*
import jenkins.model.Jenkins

Jenkins jenkins = Jenkins.instance
def stages = [:]
def jiraIntegrationEnabled = Boolean.parseBoolean("${JIRA_INTEGRATION_ENABLED}" as String)

stages['build-application-groovy'] = '[{"name": "checkout"},{"name": "config-files-validation"},' +
        '{"name": "shutdown-services"},{"name": "create-backup"},{"name": "get-changes"},' +
        '{"name": "deploy-data-model"},{"name": "upload-global-vars-changes"},' +
        '{"name": "create-keycloak-roles"},{"name": "upload-business-process-changes"},' +
        '{"name": "create-permissions-business-process"},{"name": "upload-form-changes"},' +
        '{"name": "create-reports"},{"name": "run-autotests"}]'

stages['data-model'] = '[{"name": "checkout"},{"name": "create-schema"},' +
        '{"name": "create-repositories"},{"name": "clone-projects"},' +
        '{"name": "generate-projects"},{"name": "commit-projects"}]'

stages['Delete-release'] = '[{"name": "checkout"},{"name": "delete-registry"}]'

stages['Cleanup-pipe'] = '[{"name": "cleanup-trigger"}]'

def codebaseName = "${NAME}"
def gitServerCrName = "${GIT_SERVER_CR_NAME}"
def gitServerCrVersion = "${GIT_SERVER_CR_VERSION}"
def gitCredentialsId = "${GIT_CREDENTIALS_ID ? GIT_CREDENTIALS_ID : 'gerrit-ciuser-sshkey'}"
def repositoryPath = "${REPOSITORY_PATH}"

def codebaseFolder = jenkins.getItem(codebaseName)
if (codebaseFolder == null) {
    folder(codebaseName)
}

createCiPipeline("Build-${codebaseName}", codebaseName, stages["build-application-groovy"], "build.groovy",
        repositoryPath, gitCredentialsId, "master", gitServerCrName, gitServerCrVersion, true)

createCiPipeline("Build-${codebaseName}-data-model", codebaseName, stages["data-model"], "build.groovy",
        repositoryPath, gitCredentialsId, "master", gitServerCrName, gitServerCrVersion, false)

createReleaseDeletePipeline("Delete-release-${codebaseName}", codebaseName, stages["Delete-release"], "build.groovy",
        repositoryPath, gitCredentialsId, "master", gitServerCrName, gitServerCrVersion)

createCleanUpPipeline("cleanup-job", codebaseName, stages["Cleanup-pipe"], "build.groovy",
        repositoryPath, gitCredentialsId, "master", gitServerCrName, gitServerCrVersion)

def createCiPipeline(pipelineName, codebaseName, codebaseStages, pipelineScript, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion, buildTrigger = "true") {
    def pipelineFullName = "${codebaseName}/${watchBranch.toUpperCase().replaceAll(/\//, "-")}-${pipelineName}"
    pipelineJob(pipelineFullName) {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
        concurrentBuild(false)
        if (buildTrigger) {
            triggers {
                gerrit {
                    events {
                        if (pipelineName.contains("Build"))
                            changeMerged()
                        else
                            patchsetCreated()
                    }
                    project("plain:${codebaseName}", ["plain:${watchBranch}"])
                }
            }
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repository)
                            credentials(credId)
                        }
                        branches("${watchBranch}")
                        scriptPath("${pipelineScript}")
                    }
                }
                parameters {
                    booleanParam("FULL_DEPLOY", false, "Select to deploy all files, not only changed")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    if (pipelineName.contains("Build"))
                        stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
    if (buildTrigger && pipelineName.contains("Build"))
        queue pipelineFullName
}

def createReleaseDeletePipeline(pipelineName, codebaseName, codebaseStages, pipelineScript, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }

        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repository)
                            credentials(credId)
                        }
                        branches("${watchBranch}")
                        scriptPath("${pipelineScript}")
                    }
                }
                parameters {
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}

def createCleanUpPipeline(pipelineName, codebaseName, codebaseStages, pipelineScript, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion) {
    pipelineJob("cleanup-job") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }

        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repository)
                            credentials(credId)
                        }
                        branches("${watchBranch}")
                        scriptPath("${pipelineScript}")
                    }
                }
                parameters {
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}