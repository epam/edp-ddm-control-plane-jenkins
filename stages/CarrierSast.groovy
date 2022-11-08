/* Copyright 2021 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "carrier-sast", buildTool = "any", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class CarrierSast {
    Script script
    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "carrier-credentials",
                    passwordVariable: 'TOKEN', usernameVariable: 'USERNAME')]) {
                script.sh "sed 's/PROJECT-NAME/${context.codebase.name}/' /tmp/carrier-config/config.yaml > ./config.yaml"
                script.sh "dusty run -s sastJavaScript -c ./config.yaml"
                script.archiveArtifacts artifacts: 'report.html, report.xml'
            }
        }
    }
}
return CarrierSast
