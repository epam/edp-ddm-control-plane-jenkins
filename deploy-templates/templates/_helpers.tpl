{{/*
Expand the name of the chart.
*/}}
{{- define "jenkins-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "jenkins-operator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "jenkins-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "imageRegistry" -}}
{{- if .Values.global.imageRegistry -}}
{{- printf "%s/" .Values.global.imageRegistry -}}
{{- else -}}
{{- end -}}
{{- end }}

{{- define "edp.hostnameSuffix" -}}
{{- printf "%s-%s.%s" .Values.cdPipelineName .Values.cdPipelineStageName .Values.dnsWildcard }}
{{- end }}

{{- define "keycloak.realm" -}}
{{- printf "%s-%s" .Release.Namespace .Values.keycloak.realm }}
{{- end -}}

{{/*
Define Jenkins URL
*/}}
{{- define "jenkins-operator.jenkinsBaseUrl" -}}
{{- if .Values.cdPipelineName -}}
{{- printf "%s-%s-%s" "jenkins" .Values.edpProject (include "edp.hostnameSuffix" .) }}
{{- else -}}
{{- printf "%s-%s.%s" "jenkins" .Values.edpProject .Values.dnsWildcard }}
{{- end -}}
{{- end }}

{{/*
Define Jenkins URL
*/}}
{{- define "jenkins-operator.jenkinsUrl" -}}
{{- printf "%s%s" "https://" (include "jenkins-operator.jenkinsBaseUrl" .) }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "jenkins-operator.metaLabels" -}}
helm.sh/chart: {{ include "jenkins-operator.chart" . }}
{{ include "jenkins-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "jenkins-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "jenkins-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "gerrit.gitUser" -}}
{{- $root := .root }}
{{- $gitServer := (lookup "v2.edp.epam.com/v1alpha1" "GitServer" $root.Release.Namespace "gerrit") -}}
{{- $gitServer.spec.gitUser }}
{{- end }}