package com.xiaoai.agent.collaboration.strategy;

import com.xiaoai.agent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationStrategyRegistryTest {

    @Test
    void getStrategyShouldReturnRegisteredStrategyByType() {
        CollaborationStrategy strategy = new StubStrategy("orchestrated_team");
        CollaborationStrategyRegistry registry = new CollaborationStrategyRegistry(List.of(strategy));

        CollaborationStrategy result = registry.getStrategy("orchestrated_team");

        assertThat(result).isSameAs(strategy);
    }

    @Test
    void getStrategyShouldRejectMissingStrategyType() {
        CollaborationStrategyRegistry registry = new CollaborationStrategyRegistry(List.of(new StubStrategy("orchestrated_team")));

        assertThatThrownBy(() -> registry.getStrategy("missing_strategy"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration strategy not found: missing_strategy");
    }

    private static class StubStrategy implements CollaborationStrategy {

        private final String strategyType;

        private StubStrategy(String strategyType) {
            this.strategyType = strategyType;
        }

        @Override
        public String strategyType() {
            return strategyType;
        }

        @Override
        public CollaborationStrategyResult start(CollaborationStrategyContext context) {
            return CollaborationStrategyResult.started(0, 0);
        }

        @Override
        public CollaborationStrategyResult continueAfterGate(CollaborationStrategyContext context) {
            return CollaborationStrategyResult.continued(0);
        }
    }
}
