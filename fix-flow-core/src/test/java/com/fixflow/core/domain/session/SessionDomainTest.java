package com.fixflow.core.domain.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionDomainTest {

    private FIXSessionConfig valid() {
        return new FIXSessionConfig(UUID.randomUUID(), "sess", FIXMode.INITIATOR,
                FIXVersion.FIX_44, "9", "SENDER", "TARGET", "localhost", 9001,
                30, true, false);
    }

    @Test
    void keepsAllAccessors() {
        UUID id = UUID.randomUUID();
        FIXSessionConfig c = new FIXSessionConfig(id, "sess", FIXMode.ACCEPTOR,
                FIXVersion.FIXT_11, "9", "S", "T", "host", 1234, 45, false, true);

        assertThat(c.id()).isEqualTo(id);
        assertThat(c.name()).isEqualTo("sess");
        assertThat(c.mode()).isEqualTo(FIXMode.ACCEPTOR);
        assertThat(c.fixVersion()).isEqualTo(FIXVersion.FIXT_11);
        assertThat(c.defaultApplVerID()).isEqualTo("9");
        assertThat(c.senderCompID()).isEqualTo("S");
        assertThat(c.targetCompID()).isEqualTo("T");
        assertThat(c.host()).isEqualTo("host");
        assertThat(c.port()).isEqualTo(1234);
        assertThat(c.heartbeatInterval()).isEqualTo(45);
        assertThat(c.resetOnLogon()).isFalse();
        assertThat(c.resetOnLogout()).isTrue();
    }

    @Test
    void acceptsBoundaryPorts() {
        assertThatCode(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_42, null, "S", "T", "h", 1, 1, false, false)).doesNotThrowAnyException();
        assertThatCode(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_42, null, "S", "T", "h", 65535, 1, false, false)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new FIXSessionConfig(null, "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", "T", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("session id required");
    }

    @Test
    void rejectsNullFixVersion() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                null, null, "S", "T", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fixVersion required");
    }

    @Test
    void rejectsNullMode() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", null,
                FIXVersion.FIX_44, null, "S", "T", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mode required");
    }

    @Test
    void rejectsNullSenderCompID() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, null, "T", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("senderCompID required");
    }

    @Test
    void rejectsBlankSenderCompID() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "  ", "T", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("senderCompID required");
    }

    @Test
    void rejectsNullTargetCompID() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", null, "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetCompID required");
    }

    @Test
    void rejectsBlankTargetCompID() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", " ", "h", 9001, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetCompID required");
    }

    @Test
    void rejectsPortBelowRange() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", "T", "h", 0, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("port must be 1..65535");
    }

    @Test
    void rejectsPortAboveRange() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", "T", "h", 65536, 30, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("port must be 1..65535");
    }

    @Test
    void rejectsHeartbeatBelowOne() {
        assertThatThrownBy(() -> new FIXSessionConfig(UUID.randomUUID(), "s", FIXMode.INITIATOR,
                FIXVersion.FIX_44, null, "S", "T", "h", 9001, 0, false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeatInterval must be >= 1");
    }

    @Test
    void validConfigEqualityHolds() {
        assertThat(valid()).isNotNull();
        FIXSessionConfig a = valid();
        assertThat(a).isEqualTo(a).hasSameHashCodeAs(a);
        assertThat(a.toString()).contains("SENDER");
    }

    @Test
    void fixModeEnumValues() {
        assertThat(FIXMode.values()).containsExactly(FIXMode.INITIATOR, FIXMode.ACCEPTOR);
        assertThat(FIXMode.valueOf("ACCEPTOR")).isEqualTo(FIXMode.ACCEPTOR);
    }

    @Test
    void fixVersionEnumValues() {
        assertThat(FIXVersion.values()).containsExactly(
                FIXVersion.FIX_42, FIXVersion.FIX_44, FIXVersion.FIXT_11);
        assertThat(FIXVersion.valueOf("FIXT_11")).isEqualTo(FIXVersion.FIXT_11);
    }
}
