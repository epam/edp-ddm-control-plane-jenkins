import groovy.json.*
import jenkins.model.Jenkins

Jenkins jenkins = Jenkins.instance
def stages = [:]
def jiraIntegrationEnabled = Boolean.parseBoolean("${JIRA_INTEGRATION_ENABLED}" as String)
def commitValidateStage = jiraIntegrationEnabled ? ',{"name": "commit-validate"}' : ''
def createJFVStage = jiraIntegrationEnabled ? ',{"name": "create-jira-fix-version"}' : ''
def buildTool = "${BUILD_TOOL}"
def goBuildStage = buildTool.toString() == "go" ? ',{"name": "build"}' : ',{"name": "compile"}'

stages['Code-review-application'] = '[{"name": "gerrit-checkout"}' + "${commitValidateStage}" + goBuildStage +
        ',{"name": "tests"},{"name": "sonar"}]'
stages['Code-review-library'] = '[{"name": "gerrit-checkout"}' + "${commitValidateStage}" +
        ',{"name": "compile"},{"name": "tests"},{"name": "sonar"}]'
stages['Code-review-autotests'] = '[{"name": "gerrit-checkout"}' + "${commitValidateStage}" +
        ',{"name": "tests"},{"name": "sonar"}]'
stages['Code-review-default'] = '[{"name": "gerrit-checkout"}' + "${commitValidateStage}" + ']'

stages['Build-library-maven'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},{"name": "push"}' + "${createJFVStage}" + ',{"name": "git-tag"}]'
stages['Build-library-npm'] = stages['Build-library-maven']
stages['Build-library-gradle'] = stages['Build-library-maven']
stages['Build-library-dotnet'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "push"}' + "${createJFVStage}" + ',{"name": "git-tag"}]'

stages['Build-application-maven'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},{"name": "build-image-from-dockerfile"},' +
        '{"name": "push"}' + "${createJFVStage}" + ',{"name": "git-tag"}]'
stages['Build-application-npm'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},{"name": "build-image-from-dockerfile"},' +
        '{"name": "push"}' + "${createJFVStage}" + ',{"name": "git-tag"}]'
stages['Build-application-gradle'] = stages['Build-application-maven']
stages['Build-application-dotnet'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build-image-from-dockerfile"},' +
        '{"name": "push"}' + "${createJFVStage}" + ',{"name": "git-tag"}]'
stages['Build-application-go'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "tests"},{"name": "sonar"},' +
        '{"name": "build"},{"name": "build-image-from-dockerfile"}' +
        "${createJFVStage}" + ',{"name": "git-tag"}]'
stages['Build-application-python'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},{"name": "tests"},{"name": "sonar"},' +
        '{"name": "build-image-from-dockerfile"},{"name": "push"}' + "${createJFVStage}" +
        ',{"name": "git-tag"}]'

stages['Build-application-gitops'] = '[{"name": "checkout"},{"name": "deploy-via-helmfile"}]'
stages['Build-library-gitops'] = '[{"name": "checkout"},{"name": "deploy-via-helmfile"}]'
stages['Build-autotests-gitops'] = '[{"name": "checkout"},{"name": "deploy-via-helmfile"}]'
stages['Build-clustermgmt-gitops'] = '[{"name": "checkout"},{"name": "deploy-via-helmfile"}]'
stages['Build-registry-gitops'] = '[{"name": "checkout"},{"name": "validating-values"},{"name": "registry-integration"},{"name": "deploy-via-helmfile"}]'
stages['Build-registry-group-gitops'] = '[{"name": "checkout"},{"name": "deploy-registry-group"}]'


stages['Create-release'] = '[{"name": "checkout"},{"name": "create-branch"},{"name": "trigger-job"}]'
stages['Delete-release'] = '[{"name": "checkout"},{"name": "delete-registry"}]'
stages['Delete-registry-group'] = '[{"name": "checkout"},{"name": "delete-registry-group"}]'

stages['Create-registry-backup'] = '[{"name": "checkout"},{"name": "create-backup"}]'
stages['Restore-registry'] = '[{"name": "checkout"},{"name": "cleanup-registry-before-restore"},{"name": "restore-registry-bucket"}]'

def buildToolsOutOfTheBox = ["maven","npm","gradle","dotnet","none","go","python"]
def defaultBuild = '[{"name": "checkout"}]'

def codebaseName = "${NAME}"
def gitServerCrName = "${GIT_SERVER_CR_NAME}"
def gitServerCrVersion = "${GIT_SERVER_CR_VERSION}"
def gitCredentialsId = "${GIT_CREDENTIALS_ID ? GIT_CREDENTIALS_ID : 'gerrit-ciuser-sshkey'}"
def repositoryPath = "${REPOSITORY_PATH}"
def edpLibraryStagesVersion = "${EDP_LIBRARY_STAGES_VERSION}"
def edpLibraryPipelinesVersion = "${EDP_LIBRARY_PIPELINES_VERSION}"
def codebaseFolder = jenkins.getItem(codebaseName)
if (codebaseFolder == null) {
    folder(codebaseName)
}

createListView(codebaseName, "Releases")
createReleasePipeline("Create-release-${codebaseName}", codebaseName, stages["Create-release"],
        repositoryPath, gitCredentialsId, gitServerCrName, gitServerCrVersion, jiraIntegrationEnabled, edpLibraryStagesVersion, edpLibraryPipelinesVersion)

if (buildTool.toString().equalsIgnoreCase('none')) {
    return true
}

if (BRANCH) {
    def branch = "${BRANCH}"
    def formattedBranch = "${branch.toUpperCase().replaceAll(/\//, "-")}"
    createListView(codebaseName, formattedBranch)

    def type = "${TYPE}"
    def supBuildTool = buildToolsOutOfTheBox.contains(buildTool.toString())
    def crKey = supBuildTool ? "Code-review-${type}" : "Code-review-default"
    createCiPipeline("Code-review-${codebaseName}", codebaseName, stages[crKey], "CodeReview",
            repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion)

    def buildKey = "Build-${type}-${buildTool.toLowerCase()}".toString()
    if ( type.equalsIgnoreCase('registry') || type.equalsIgnoreCase('clustermgmt') || type.equalsIgnoreCase('registry-group')) {
        def jobExists = false
        if("${formattedBranch}-Build-${codebaseName}".toString() in Jenkins.instance.getAllItems().collect{it.name})
            jobExists = true

        createCiPipeline("Build-${codebaseName}", codebaseName, stages.get(buildKey, defaultBuild), "Build",
                repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion)

        if(!type.equalsIgnoreCase('registry-group'))
            createReleaseDeletePipeline("Delete-release-${codebaseName}", codebaseName, stages["Delete-release"],
                repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion)

        // only needed for registryGroup
        if (type.equalsIgnoreCase('registry-group')) {
            createReleaseDeletePipeline("Delete-release-${codebaseName}", codebaseName, stages["Delete-registry-group"],
                    repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion)
        }

        // only needed for registry
        Boolean isRegistryBackupEnabled = false
        String schedule
        String scriptPath = "/tmp/parse_yaml.sh"
        String valuesFolderPath = "/tmp/${codebaseName}/deploy-templates"
        String parsedValuesPath = "${valuesFolderPath}/parsed_values.yaml"

        //delete the folder of the previously downloaded registry
        def rmProc = new ProcessBuilder( 'sh', '-c', "rm -rf /tmp/${codebaseName}").redirectErrorStream(true).start()
        //wait for the command execution
        rmProc.waitForOrKill(1000)
        //clone registry repository
        new ProcessBuilder( 'sh', '-c', "git clone ${repositoryPath} /tmp/${codebaseName}").redirectErrorStream(true).start().text
        //create the file for script
        def createFileProc = new ProcessBuilder( 'sh', '-c', "touch ${scriptPath}").redirectErrorStream(true).start()
        //wait for the command execution
        createFileProc.waitForOrKill(1000)
        //put the script into the created file
        def putScriptProc = new ProcessBuilder( 'sh', '-c', "echo \"#!/bin/sh\n" +
                "function parse_yaml {\n" +
                "\tsed 's/  / /g' ${valuesFolderPath}/values.yaml > ${valuesFolderPath}/updated_values.yaml\n" +
                "\tlocal prefix=\\\$2\n" +
                "\tlocal s='[[:space:]]*' w='[a-zA-Z0-9_]*' fs=\\\$(echo @|tr @ '\\034')\n" +
                "\tsed -ne \\\"s|,\\\$s\\]\\\$s\\\\\\\$|]|\\\" \\\\\n" +
                "\t\t-e \\\":1;s|^\\(\\\$s\\)\\(\\\$w\\)\\\$s:\\\$s\\[\\\$s\\(.*\\)\\\$s,\\\$s\\(.*\\)\\\$s\\]|\\1\\2: [\\3]\\n\\1  - \\4|;t1\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)\\(\\\$w\\)\\\$s:\\\$s\\[\\\$s\\(.*\\)\\\$s\\]|\\1\\2:\\n\\1  - \\3|;p\\\" \\\$1 | \\\\\n" +
                "\tsed -ne \\\"s|,\\\$s}\\\$s\\\\\\\$|}|\\\" \\\\\n" +
                "\t\t-e \\\":1;s|^\\(\\\$s\\)-\\\$s{\\\$s\\(.*\\)\\\$s,\\\$s\\(\\\$w\\)\\\$s:\\\$s\\(.*\\)\\\$s}|\\1- {\\2}\\n\\1  \\3: \\4|;t1\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)-\\\$s{\\\$s\\(.*\\)\\\$s}|\\1-\\n\\1  \\2|;p\\\" | \\\\\n" +
                "\tsed -ne \\\"s|^\\(\\\$s\\):|\\1|\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)-\\\$s[\\\\\\\"']\\(.*\\)[\\\\\\\"']\\\$s\\\\\\\$|\\1\\\$fs\\\$fs\\2|p\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)-\\\$s\\(.*\\)\\\$s\\\\\\\$|\\1\\\$fs\\\$fs\\2|p\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)\\(\\\$w\\)\\\$s:\\\$s[\\\\\\\"']\\(.*\\)[\\\\\\\"']\\\$s\\\\\\\$|\\1\\\$fs\\2\\\$fs\\3|p\\\" \\\\\n" +
                "\t\t-e \\\"s|^\\(\\\$s\\)\\(\\\$w\\)\\\$s:\\\$s\\(.*\\)\\\$s\\\\\\\$|\\1\\\$fs\\2\\\$fs\\3|p\\\" | \\\\\n" +
                "\tawk -F\\\$fs '{\n" +
                "\t\tindent = length(\\\$1)/2;\n" +
                "\t\tvname[indent] = \\\$2;\n" +
                "\t\tfor (i in vname) {if (i > indent) {delete vname[i]; idx[i]=0}}\n" +
                "\t\tif(length(\\\$2)== 0) {vname[indent]= ++idx[indent]};\n" +
                "\t\tif (length(\\\$3) > 0) {\n" +
                "\t\t\tvn=\\\"\\\"; for (i=0; i<indent; i++) {vn=(vn)(vname[i])(\\\"_\\\")}\n" +
                "\t\t\tprintf(\\\"%s%s%s=\\\\\\\"%s\\\\\\\"\\\\n\\\", \\\"'\\\$prefix'\\\",vn, vname[indent], \\\$3);\n" +
                "\t\t}\n" +
                "\t}'\n" +
                "}\n" +
                "parse_yaml ${valuesFolderPath}/updated_values.yaml > ${parsedValuesPath}\" > ${scriptPath}").redirectErrorStream(true).start()
        putScriptProc.waitForOrKill(1000)
        //make a script executable
        def makeExecProc = new ProcessBuilder( 'sh', '-c', "chmod +x ${scriptPath}").redirectErrorStream(true).start()
        makeExecProc.waitForOrKill(1000)
        //run a script for yaml parsing
        def runScriptProc = new ProcessBuilder( 'sh', '-c', ".${scriptPath}").redirectErrorStream(true).start()
        runScriptProc.waitForOrKill(1000)
        def valuesConfig = new ConfigSlurper().parse(new File("${parsedValuesPath}").toURL())
        valuesConfig.each { key, value ->
            key == 'global_registryBackup_enabled' ? isRegistryBackupEnabled = value.toBoolean() : false
            (key == 'global_registryBackup_schedule' && isRegistryBackupEnabled) ? schedule = value : ""
        }
        //rm temp files
        def rmBackupValuesFilesProc = new ProcessBuilder( 'sh', '-c', "rm -rf ${valuesFolderPath}/updated_values.yaml ${parsedValuesPath} ${scriptPath}").redirectErrorStream(true).start()
        rmBackupValuesFilesProc.waitForOrKill(1000)

        if (type.equalsIgnoreCase('registry')) {
            createReleaseBackupPipeline("Create-registry-backup-${codebaseName}", codebaseName, stages["Create-registry-backup"],
                    repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion,
                    isRegistryBackupEnabled, schedule)

            createReleaseRestorePipeline("Restore-registry-${codebaseName}", codebaseName, stages["Restore-registry"],
                    repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion)
        }

        if(!jobExists)
            queue("${codebaseName}/${formattedBranch}-Build-${codebaseName}")
    }
}

def createCiPipeline(pipelineName, codebaseName, codebaseStages, makeAction, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion) {
    pipelineJob("${codebaseName}/${watchBranch.toUpperCase().replaceAll(/\//, "-")}-${pipelineName}") {
        if(watchBranch == "master" && pipelineName.startsWith("Build"))
            concurrentBuild(false)
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
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
        definition {
            cps {
                script("@Library([\"edp-library-stages@${edpLibraryStagesVersion}\", \"edp-library-pipelines@${edpLibraryPipelinesVersion}\"]) _ \n${makeAction}()")
                sandbox(true)
                parameters {
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    if (pipelineName.contains("Build"))
                        stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}

def createReleasePipeline(pipelineName, codebaseName, codebaseStages, repository, credId,
                          gitServerCrName, gitServerCrVersion, jiraIntegrationEnabled, edpLibraryStagesVersion, edpLibraryPipelinesVersion) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(14)
            daysToKeep(30)
        }
        definition {
            cps {
                script("@Library([\"edp-library-stages@${edpLibraryStagesVersion}\", \"edp-library-pipelines@${edpLibraryPipelinesVersion}\"]) _ \nCreateRelease()")
                sandbox(true)
                parameters {
                    stringParam("STAGES", "${codebaseStages}", "")
                    if (pipelineName.contains("Create-release") || pipelineName.contains("Delete-release")) {
                        stringParam("JIRA_INTEGRATION_ENABLED", "${jiraIntegrationEnabled}", "Is Jira integration enabled")
                        stringParam("GERRIT_PROJECT", "${codebaseName}", "")
                        stringParam("RELEASE_NAME", "", "Name of the release(branch to be created)")
                        stringParam("COMMIT_ID", "", "Commit ID that will be used to create branch from for new release. If empty, HEAD of master will be used")
                        stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                        stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                        stringParam("REPOSITORY_PATH", "${repository}", "Full repository path")
                    }
                    if (pipelineName.contains("Delete-release"))
                        stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}

def createListView(codebaseName, branchName) {
    listView("${codebaseName}/${branchName}") {
        if (branchName.toLowerCase() == "releases") {
            jobFilters {
                regex {
                    matchType(MatchType.INCLUDE_MATCHED)
                    matchValue(RegexMatchValue.NAME)
                    regex("^Create-release.*")
                }
            }
        } else {
            jobFilters {
                regex {
                    matchType(MatchType.INCLUDE_MATCHED)
                    matchValue(RegexMatchValue.NAME)
                    regex("^${branchName}-(Code-review|Build).*")
                }
            }
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }
}

def createReleaseDeletePipeline(pipelineName, codebaseName, codebaseStages, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
        definition {
            cps {
                script("@Library([\"edp-library-stages@${edpLibraryStagesVersion}\", \"edp-library-pipelines@${edpLibraryPipelinesVersion}\"]) _ \nBuild()")
                sandbox(true)
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

def createReleaseBackupPipeline(pipelineName, codebaseName, codebaseStages, repository, credId, watchBranch = "master",
                                gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion,
                                isRegistryBackupEnabled, schedule) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
        if (isRegistryBackupEnabled.toBoolean()) {
            triggers {
                cron("TZ=Europe/Kiev\n" + schedule)
            }
        }
        definition {
            cps {
                script("@Library([\"edp-library-stages@${edpLibraryStagesVersion}\", \"edp-library-pipelines@${edpLibraryPipelinesVersion}\"]) _ \nBuild()")
                sandbox(true)
                parameters {
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}


def createReleaseRestorePipeline(pipelineName, codebaseName, codebaseStages, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion, edpLibraryStagesVersion, edpLibraryPipelinesVersion) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
        definition {
            cps {
                script("@Library([\"edp-library-stages@${edpLibraryStagesVersion}\", \"edp-library-pipelines@${edpLibraryPipelinesVersion}\"]) _ \nBuild()")
                sandbox(true)
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