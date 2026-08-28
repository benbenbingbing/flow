package com.workflow.embed.management.crypto;

import java.util.List;

/** 外部 Subject 摘要端口；原始 Subject 绝不能越过该边界进入持久化。 */
public interface EmbedSubjectDigester {

    Digest current(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject);

    List<Digest> accepted(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject);

    record Digest(String value, String keyVersion) {
    }
}
