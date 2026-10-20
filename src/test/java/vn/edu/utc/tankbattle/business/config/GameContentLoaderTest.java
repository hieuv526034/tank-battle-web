package vn.edu.utc.tankbattle.business.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ItemType;

class GameContentLoaderTest {
    @Test
    void loadsAnimalEnemiesWithDistinctScoresAndHealingDamageItems() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            GameContentLoader catalog =
                    new GameContentLoader(new ObjectMapper(), factory.getValidator());
            assertThat(catalog.definition().enemies()).hasSize(4);
            assertThat(catalog.enemy("BIRD_SCOUT")).isPresent();
            assertThat(catalog.enemy("BUTTERFLY_SWARM").orElseThrow().killScore()).isEqualTo(10);
            assertThat(catalog.enemy("BEETLE_ARMORED").orElseThrow().health()).isEqualTo(5);
            assertThat(catalog.definition().items())
                    .anySatisfy(
                            item -> {
                                assertThat(item.effect()).isEqualTo(ItemType.DAMAGE_BOOST);
                                assertThat(item.amount()).isGreaterThan(0);
                                assertThat(item.durationTicks()).isGreaterThan(0);
                            })
                    .anySatisfy(item -> assertThat(item.effect()).isEqualTo(ItemType.REPAIR));
        }
    }
}
