package com.purpurflow.server.session;

import java.util.UUID;

/**
 * 重连补偿（H4 关键接口契约）。
 * resume(sessionId, epoch, ackSeq) -> ResumePlan(FULL_SNAPSHOT | DELTA, fromSeq)。
 */
public interface ResumeService {
    ResumePlan resume(UUID sessionId, long epoch, long ackSeq);

    /** 补偿计划：类型 + 起始 seq（FULL_SNAPSHOT 时 fromSeq 忽略）。 */
    record ResumePlan(Kind kind, long fromSeq) {
        public enum Kind { FULL_SNAPSHOT, DELTA, NONE }

        public static ResumePlan full() { return new ResumePlan(Kind.FULL_SNAPSHOT, -1); }
        public static ResumePlan delta(long fromSeq) { return new ResumePlan(Kind.DELTA, fromSeq); }
        public static ResumePlan none() { return new ResumePlan(Kind.NONE, -1); }
    }
}
