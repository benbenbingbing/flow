{{- define "flow.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "flow.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "flow.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "flow.labels" -}}
app.kubernetes.io/name: {{ include "flow.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "flow.selectorLabels" -}}
app.kubernetes.io/name: {{ include "flow.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "flow.componentLabels" -}}
{{ include "flow.selectorLabels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "flow.image" -}}
{{- if .image.digest -}}
{{- printf "%s@%s" .image.repository .image.digest -}}
{{- else if .root.Values.global.allowMutableImages -}}
{{- printf "%s:%s" .image.repository .image.tag -}}
{{- else -}}
{{- fail "immutable image digest is required" -}}
{{- end -}}
{{- end -}}

{{- define "flow.secretEnv" -}}
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: db-username
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: db-password
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: jwt-secret
- name: CONFIG_MIGRATION_SIGNING_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: config-migration-signing-key
- name: FILE_STORAGE_S3_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: s3-access-key
- name: FILE_STORAGE_S3_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: s3-secret-key
{{- end -}}

{{- define "flow.schemaSecretEnv" -}}
- name: SCHEMA_DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: schema-db-username
- name: SCHEMA_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.existingSecret }}
      key: schema-db-password
{{- end -}}
